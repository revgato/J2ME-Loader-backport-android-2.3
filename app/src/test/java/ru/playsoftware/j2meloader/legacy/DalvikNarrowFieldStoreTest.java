package ru.playsoftware.j2meloader.legacy;

import com.android.dex.ClassData;
import com.android.dex.ClassDef;
import com.android.dex.Code;
import com.android.dex.Dex;
import com.android.dx.command.dexer.Main;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for old Dalvik's strict narrow field-store verifier. */
public class DalvikNarrowFieldStoreTest {
    private static final int IPUT_BYTE = 0x5d;
    private static final int IPUT_CHAR = 0x5e;
    private static final int IPUT_SHORT = 0x5f;
    private static final int SPUT_BYTE = 0x6b;
    private static final int SPUT_CHAR = 0x6c;
    private static final int SPUT_SHORT = 0x6d;
    private static final int INT_TO_BYTE = 0x8d;
    private static final int INT_TO_CHAR = 0x8e;
    private static final int INT_TO_SHORT = 0x8f;

    @Test
    public void narrowsJvmFieldStoresBeforeIputAndSput() throws Exception {
        File root = tempDirectory();
        try {
            File jar = new File(root, "narrow-field-store.jar");
            File dex = new File(root, "narrow-field-store.dex");
            writeJar(jar, "compat/PrimitiveFieldStore.class", primitiveFieldStoreClass());

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            try {
                Main.run(LegacyInstaller.dexArguments(jar, dex),
                        new com.android.dx.command.dexer.DxContext(stdout, stderr));
            } catch (IOException error) {
                throw new IOException("dx output: " + stdout + " " + stderr, error);
            }

            int[] stores = new int[]{
                    IPUT_BYTE, IPUT_CHAR, IPUT_SHORT, SPUT_BYTE, SPUT_CHAR, SPUT_SHORT};
            int[] conversions = new int[]{
                    INT_TO_BYTE, INT_TO_CHAR, INT_TO_SHORT,
                    INT_TO_BYTE, INT_TO_CHAR, INT_TO_SHORT};
            int[] counts = new int[stores.length];
            for (Code code : findCodeItems(new Dex(dex))) {
                short[] instructions = code.getInstructions();
                for (int i = 0; i < instructions.length; i++) {
                    int opcode = instructions[i] & 0xff;
                    for (int store = 0; store < stores.length; store++) {
                        if (opcode == stores[store]) {
                            counts[store]++;
                            assertTrue("narrow field store must have a preceding conversion", i > 0);
                            assertEquals("narrowing conversion must immediately precede field store",
                                    conversions[store], instructions[i - 1] & 0xff);
                        }
                    }
                }
            }
            assertEquals(1, counts[0]);
            assertEquals(1, counts[1]);
            assertEquals(1, counts[2]);
            assertEquals(1, counts[3]);
            assertEquals(1, counts[4]);
            assertEquals(1, counts[5]);
        } finally {
            delete(root);
        }
    }

    private static List<Code> findCodeItems(Dex dex) throws Exception {
        List<Code> result = new ArrayList<Code>();
        Method readClassData = Dex.Section.class.getDeclaredMethod("readClassData");
        Method readCode = Dex.Section.class.getDeclaredMethod("readCode");
        readClassData.setAccessible(true);
        readCode.setAccessible(true);
        for (ClassDef classDef : dex.classDefs()) {
            Dex.Section data = dex.open(classDef.getClassDataOffset());
            ClassData classData = (ClassData) readClassData.invoke(data);
            addCodeItems(dex, classData.getDirectMethods(), readCode, result);
            addCodeItems(dex, classData.getVirtualMethods(), readCode, result);
        }
        return result;
    }

    private static void addCodeItems(Dex dex, ClassData.Method[] methods, Method readCode,
            List<Code> result) throws Exception {
        for (ClassData.Method method : methods) {
            if (method.getCodeOffset() != 0) {
                result.add((Code) readCode.invoke(dex.open(method.getCodeOffset())));
            }
        }
    }

    private static byte[] primitiveFieldStoreClass() {
        ClassWriter writer = new ClassWriter(0);
        String owner = "compat/PrimitiveFieldStore";
        writer.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                owner, null, "java/lang/Object", null);
        addField(writer, "instanceByte", "B");
        addField(writer, "instanceChar", "C");
        addField(writer, "instanceShort", "S");
        addField(writer, "staticByte", "B");
        addField(writer, "staticChar", "C");
        addField(writer, "staticShort", "S");

        MethodVisitor value = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "value", "()I", null, null);
        value.visitCode();
        value.visitIntInsn(Opcodes.BIPUSH, 123);
        value.visitInsn(Opcodes.IRETURN);
        value.visitMaxs(1, 0);
        value.visitEnd();

        emitInstanceStore(writer, owner, "instanceByte", "B");
        emitInstanceStore(writer, owner, "instanceChar", "C");
        emitInstanceStore(writer, owner, "instanceShort", "S");
        emitStaticStore(writer, owner, "staticByte", "B");
        emitStaticStore(writer, owner, "staticChar", "C");
        emitStaticStore(writer, owner, "staticShort", "S");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addField(ClassWriter writer, String name, String descriptor) {
        int access = Opcodes.ACC_PUBLIC;
        if (name.startsWith("static")) access |= Opcodes.ACC_STATIC;
        FieldVisitor field = writer.visitField(access, name, descriptor, null, null);
        field.visitEnd();
    }

    private static void emitInstanceStore(ClassWriter writer, String owner, String field,
            String descriptor) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "store" + field, "(L" + owner + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "value", "()I", false);
        method.visitFieldInsn(Opcodes.PUTFIELD, owner, field, descriptor);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
    }

    private static void emitStaticStore(ClassWriter writer, String owner, String field,
            String descriptor) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "store" + field, "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "value", "()I", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, descriptor);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
    }

    private static void writeJar(File file, String entryName, byte[] bytes) throws IOException {
        ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file));
        try {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(bytes);
            output.closeEntry();
        } finally {
            output.close();
        }
    }

    private static File tempDirectory() throws IOException {
        File file = File.createTempFile("dalvik-narrow-field-store-", "");
        if (!file.delete() || !file.mkdirs()) throw new IOException("temp directory");
        return file;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
