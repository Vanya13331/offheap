import java.lang.instrument.Instrumentation;

public class ObjectSizeAgent {
    private static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
    }

    public long getObjSize(Object obj) {
        return instrumentation.getObjectSize(obj);
    }
}
