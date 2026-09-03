package ru.playsoftware.j2meloader.legacy;

import com.android.dx.command.dexer.DxContext;
import com.android.dx.command.dexer.Main;
import com.android.dx.rop.code.RegisterSpec;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.type.Prototype;
import com.android.dx.rop.type.Type;

import com.example.legacyrepair.PrimaryFixture;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/** Ensures every dx exit path releases its process-global intern tables. */
public class MainInternCleanupTest {
    @Test
    public void clearsInternTablesAfterSuccessfulRun() throws Exception {
        File root = tempDirectory();
        File jar = null;
        try {
            jar = createFixtureJar();
            File dex = new File(root, "success.dex");
            InternSnapshot stale = seedInternTables("success");

            Main.run(LegacyInstaller.dexArguments(jar, dex), quietContext());

            assertTrue(dex.isFile());
            assertCleared(stale);
        } finally {
            if (jar != null) jar.delete();
            delete(root);
        }
    }

    @Test
    public void clearsInternTablesAfterFailedRun() throws Exception {
        File root = tempDirectory();
        try {
            File missing = new File(root, "missing.jar");
            File dex = new File(root, "failed.dex");
            InternSnapshot stale = seedInternTables("failure");

            try {
                Main.run(LegacyInstaller.dexArguments(missing, dex), quietContext());
            } catch (IOException expected) {
                // Main.run reports a non-zero dx result as IOException.
            }

            assertFalse(dex.exists());
            assertCleared(stale);
        } finally {
            delete(root);
        }
    }

    private static DxContext quietContext() {
        return new DxContext(new ByteArrayOutputStream(), new ByteArrayOutputStream());
    }

    private static InternSnapshot seedInternTables(String suffix) {
        Type type = Type.intern("Loom/" + suffix + ";");
        Prototype prototype = Prototype.intern("(Loom/" + suffix + ";)V");
        CstType cstType = CstType.intern(type);
        RegisterSpec registerSpec = RegisterSpec.make(7, type);
        return new InternSnapshot(type, prototype, cstType, registerSpec);
    }

    private static void assertCleared(InternSnapshot stale) {
        try {
            assertNotSame(stale.type, Type.intern(stale.type.getDescriptor()));
            assertNotSame(stale.prototype, Prototype.intern(stale.prototype.getDescriptor()));
            assertNotSame(stale.cstType, CstType.intern(stale.type));
            assertNotSame(stale.registerSpec, RegisterSpec.make(7, stale.type));
        } finally {
            RegisterSpec.clearInternTable();
            Prototype.clearInternTable();
            CstType.clearInternTable();
            Type.clearInternTable();
        }
    }

    private static File createFixtureJar() throws IOException {
        File jar = File.createTempFile("dx-intern-cleanup-", ".jar");
        InputStream input = PrimaryFixture.class.getResourceAsStream("PrimaryFixture.class");
        if (input == null) throw new IOException("fixture class is missing");
        JarOutputStream output = new JarOutputStream(new FileOutputStream(jar));
        try {
            output.putNextEntry(new JarEntry("com/example/legacyrepair/PrimaryFixture.class"));
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.closeEntry();
        } finally {
            input.close();
            output.close();
        }
        return jar;
    }

    private static File tempDirectory() throws IOException {
        File file = File.createTempFile("dx-intern-cleanup-", "");
        if (!file.delete() || !file.mkdirs()) throw new IOException("could not create temp dir");
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

    private static final class InternSnapshot {
        final Type type;
        final Prototype prototype;
        final CstType cstType;
        final RegisterSpec registerSpec;

        InternSnapshot(Type type, Prototype prototype, CstType cstType,
                RegisterSpec registerSpec) {
            this.type = type;
            this.prototype = prototype;
            this.cstType = cstType;
            this.registerSpec = registerSpec;
        }
    }
}
