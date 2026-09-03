package com.android.dx.command.dexer;

import com.android.dx.dex.cf.CodeStatistics;
import com.android.dx.dex.cf.OptimizerOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * State used by a single invocation of {@link Main}.
 */
public class DxContext {
    public interface ProgressListener {
        void onClassProcessed(String className);
    }

    public final CodeStatistics codeStatistics = new CodeStatistics();
    public final OptimizerOptions optimizerOptions = new OptimizerOptions();
    public final PrintStream out;
    public final PrintStream err;
    private final ProgressListener progressListener;

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    final PrintStream noop = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            // noop;
        }
    });

    public DxContext(OutputStream out, OutputStream err) {
        this(out, err, null);
    }

    public DxContext(OutputStream out, OutputStream err, ProgressListener progressListener) {
        this.out = new PrintStream(out);
        this.err = new PrintStream(err);
        this.progressListener = progressListener;
    }

    public DxContext() {
        this(System.out, System.err);
    }

    public void classProcessed(String className) {
        if (progressListener != null) {
            progressListener.onClassProcessed(className);
        }
    }
}
