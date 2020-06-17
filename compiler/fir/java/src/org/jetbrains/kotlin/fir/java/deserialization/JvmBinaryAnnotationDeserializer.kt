/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.java.deserialization

import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.deserialization.AbstractAnnotationDeserializer
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.resolve.firSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirCompositeSymbolProvider
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.load.kotlin.MemberSignature
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.NameResolver
import org.jetbrains.kotlin.metadata.deserialization.TypeTable
import org.jetbrains.kotlin.metadata.deserialization.getExtensionOrNull
import org.jetbrains.kotlin.metadata.jvm.JvmProtoBuf
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource

class JvmBinaryAnnotationDeserializer(
    val session: FirSession,
    private var byteContent: ByteArray?
) : AbstractAnnotationDeserializer(session) {
    private val storage: MutableMap<KotlinJvmBinaryClass, MemberAnnotations> = mutableMapOf()

    // TODO: Rename this once property constants are recorded as well
    private data class MemberAnnotations(val memberAnnotations: Map<MemberSignature, MutableList<FirAnnotationCall>>)

    private enum class CallableKind {
        PROPERTY_GETTER,
        PROPERTY_SETTER
    }

    override fun loadTypeAnnotations(typeProto: ProtoBuf.Type, nameResolver: NameResolver): List<FirAnnotationCall> {
        val annotations = typeProto.getExtension(JvmProtoBuf.typeAnnotation).orEmpty()
        return annotations.map { deserializeAnnotation(it, nameResolver) }
    }

    override fun loadFunctionAnnotations(
        functionProto: ProtoBuf.Function,
        nameResolver: NameResolver,
        typeTable: TypeTable,
        containerSource: DeserializedContainerSource?,
    ): List<FirAnnotationCall> {
        val signature = MemberSignature.fromJvmMemberSignature(
            JvmProtoBufUtil.getJvmMethodSignature(functionProto, nameResolver, typeTable) ?: return emptyList()
        )
        return loadMemberAnnotationsBySignature(containerSource, signature)
    }

    override fun loadConstructorAnnotations(
        constructorProto: ProtoBuf.Constructor,
        nameResolver: NameResolver,
        typeTable: TypeTable,
        containerSource: DeserializedContainerSource?
    ): List<FirAnnotationCall> {
        val signature = MemberSignature.fromJvmMemberSignature(
            JvmProtoBufUtil.getJvmConstructorSignature(constructorProto, nameResolver, typeTable) ?: return emptyList()
        )
        return loadMemberAnnotationsBySignature(containerSource, signature)
    }

    override fun loadPropertyGetterAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        nameResolver: NameResolver,
        getterFlags: Int
    ): List<FirAnnotationCall> = loadPropertyAccessorAnnotations(containerSource, propertyProto, nameResolver, CallableKind.PROPERTY_GETTER)

    override fun loadPropertySetterAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        nameResolver: NameResolver,
        setterFlags: Int
    ): List<FirAnnotationCall> = loadPropertyAccessorAnnotations(containerSource, propertyProto, nameResolver, CallableKind.PROPERTY_SETTER)

    private fun loadPropertyAccessorAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        nameResolver: NameResolver,
        kind: CallableKind
    ): List<FirAnnotationCall> {
        val signature = getPropertyAccessorSignature(propertyProto, nameResolver, kind) ?: return emptyList()
        return loadMemberAnnotationsBySignature(containerSource, signature)
    }

    private fun loadMemberAnnotationsBySignature(
        containerSource: DeserializedContainerSource?,
        signature: MemberSignature
    ): List<FirAnnotationCall> {
        val kotlinClass = containerSource?.toKotlinJvmBinaryClass() ?: return emptyList()
        return loadMemberAnnotations(kotlinClass).memberAnnotations[signature] ?: emptyList()
    }

    private fun getPropertyAccessorSignature(
        proto: ProtoBuf.Property,
        nameResolver: NameResolver,
        kind: CallableKind
    ): MemberSignature? {
        val signature = proto.getExtensionOrNull(JvmProtoBuf.propertySignature) ?: return null
        return when (kind) {
            CallableKind.PROPERTY_GETTER ->
                if (signature.hasGetter()) MemberSignature.fromMethod(nameResolver, signature.getter) else null
            CallableKind.PROPERTY_SETTER ->
                if (signature.hasSetter()) MemberSignature.fromMethod(nameResolver, signature.setter) else null
        }
    }

    private fun DeserializedContainerSource.toKotlinJvmBinaryClass(): KotlinJvmBinaryClass? =
        when (this) {
            is JvmPackagePartSource -> this.knownJvmBinaryClass
            is KotlinJvmBinarySourceElement -> this.binaryClass
            else -> null
        }

    // TODO: better to be in KotlinDeserializedJvmSymbolsProvider?
    private fun loadMemberAnnotations(kotlinClass: KotlinJvmBinaryClass): MemberAnnotations {
        if (storage.containsKey(kotlinClass)) {
            return storage[kotlinClass] ?: error("$kotlinClass should have been visited and cached.")
        }
        val memberAnnotations = hashMapOf<MemberSignature, MutableList<FirAnnotationCall>>()

        kotlinClass.visitMembers(object : KotlinJvmBinaryClass.MemberVisitor {
            override fun visitMethod(name: Name, desc: String): KotlinJvmBinaryClass.MethodAnnotationVisitor? {
                return AnnotationVisitorForMethod(MemberSignature.fromMethodNameAndDesc(name.asString(), desc))
            }

            override fun visitField(name: Name, desc: String, initializer: Any?): KotlinJvmBinaryClass.AnnotationVisitor? {
                val signature = MemberSignature.fromFieldNameAndDesc(name.asString(), desc)
                if (initializer != null) {
                    // TODO: load constant
                }
                return MemberAnnotationVisitor(signature)
            }

            inner class AnnotationVisitorForMethod(signature: MemberSignature) : MemberAnnotationVisitor(signature),
                KotlinJvmBinaryClass.MethodAnnotationVisitor {

                override fun visitParameterAnnotation(
                    index: Int,
                    classId: ClassId,
                    source: SourceElement
                ): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
                    val paramSignature = MemberSignature.fromMethodSignatureAndParameterIndex(signature, index)
                    var result = memberAnnotations[paramSignature]
                    if (result == null) {
                        result = arrayListOf()
                        memberAnnotations[paramSignature] = result
                    }
                    return loadAnnotationIfNotSpecial(classId, result)
                }
            }

            open inner class MemberAnnotationVisitor(protected val signature: MemberSignature) : KotlinJvmBinaryClass.AnnotationVisitor {
                private val result = arrayListOf<FirAnnotationCall>()

                override fun visitAnnotation(classId: ClassId, source: SourceElement): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
                    return loadAnnotationIfNotSpecial(classId, result)
                }

                override fun visitEnd() {
                    if (result.isNotEmpty()) {
                        memberAnnotations[signature] = result
                    }
                }
            }
        }, byteContent)

        byteContent = null

        val result = MemberAnnotations(memberAnnotations)
        storage[kotlinClass] = result
        return result
    }

    // TODO: Or, better to migrate annotation deserialization in KotlinDeserializedJvmSymbolsProvider to here?
    private fun loadAnnotationIfNotSpecial(
        annotationClassId: ClassId,
        result: MutableList<FirAnnotationCall>
    ): KotlinJvmBinaryClass.AnnotationArgumentVisitor? =
        (session.firSymbolProvider as? FirCompositeSymbolProvider)
            ?.providers
            ?.filterIsInstance<KotlinDeserializedJvmSymbolsProvider>()
            ?.singleOrNull()
            ?.loadAnnotationIfNotSpecial(annotationClassId, result)
}
