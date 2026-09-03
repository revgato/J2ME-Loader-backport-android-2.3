package ru.playsoftware.j2meloader.legacy;

import com.android.dex.ClassData;
import com.android.dex.ClassDef;
import com.android.dex.Code;
import com.android.dex.Dex;
import com.android.dx.command.dexer.Main;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
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

/** Regression coverage for old Dalvik's strict aput-short verifier. */
public class DalvikNarrowArrayStoreTest {
    private static final int APUT_BYTE = 0x4f;
    private static final int APUT_CHAR = 0x50;
    private static final int APUT_SHORT = 0x51;
    private static final int INT_TO_BYTE = 0x8d;
    private static final int INT_TO_CHAR = 0x8e;
    private static final int INT_TO_SHORT = 0x8f;

    @Test
    public void narrowsJvmSastoreValueBeforeAputShort() throws Exception {
        File root = tempDirectory();
        try {
            File jar = new File(root, "short-store.jar");
            File dex = new File(root, "short-store.dex");
            writeJar(jar, "compat/PrimitiveArrayStore.class", primitiveArrayStoreClass());

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            try {
                Main.run(LegacyInstaller.dexArguments(jar, dex),
                        new com.android.dx.command.dexer.DxContext(stdout, stderr));
            } catch (IOException error) {
                throw new IOException("dx output: " + stdout + " " + stderr, error);
            }

            int[] stores = new int[]{APUT_BYTE, APUT_CHAR, APUT_SHORT};
            int[] conversions = new int[]{INT_TO_BYTE, INT_TO_CHAR, INT_TO_SHORT};
            int[] counts = new int[stores.length];
            for (Code code : findCodeItems(new Dex(dex))) {
                short[] instructions = code.getInstructions();
                for (int i = 0; i < instructions.length; i++) {
                    int opcode = instructions[i] & 0xff;
                    for (int store = 0; store < stores.length; store++) {
                        if (opcode == stores[store]) {
                            counts[store]++;
                            assertTrue("narrow array store must have a preceding conversion", i > 0);
                            assertEquals("narrowing conversion must immediately precede array store",
                                    conversions[store], instructions[i - 1] & 0xff);
                        }
                    }
                }
            }
            assertEquals(1, counts[0]);
            assertEquals(1, counts[1]);
            assertEquals(1, counts[2]);
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
            ClassData.Method[] methods = classData.getDirectMethods();
            for (ClassData.Method method : methods) {
                if (method.getCodeOffset() != 0) {
                    Code code = (Code) readCode.invoke(dex.open(method.getCodeOffset()));
                    result.add(code);
                }
            }
        }
        return result;
    }

    private static byte[] primitiveArrayStoreClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "compat/PrimitiveArrayStore", null, "java/lang/Object", null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>",
                "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        emitStore(writer, "storeByte", "([BII)V", Opcodes.BASTORE);
        emitStore(writer, "storeChar", "([CII)V", Opcodes.CASTORE);
        emitStore(writer, "storeShort", "([SII)V", Opcodes.SASTORE);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void emitStore(ClassWriter writer, String name, String descriptor,
            int arrayStoreOpcode) {
        MethodVisitor store = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, descriptor, null, null);
        store.visitCode();
        store.visitVarInsn(Opcodes.ALOAD, 0);
        store.visitVarInsn(Opcodes.ILOAD, 1);
        store.visitVarInsn(Opcodes.ILOAD, 2);
        // JVM narrow array stores accept a category-1 int without explicit narrowing.
        store.visitInsn(arrayStoreOpcode);
        store.visitInsn(Opcodes.RETURN);
        store.visitMaxs(3, 3);
        store.visitEnd();
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
        File file = File.createTempFile("dalvik-narrow-store-", "");
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
