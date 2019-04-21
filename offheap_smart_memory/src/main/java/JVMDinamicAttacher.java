import sun.jvm.hotspot.gc_implementation.parallelScavenge.PSOldGen;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.ParallelScavengeHeap;
import sun.jvm.hotspot.gc_interface.CollectedHeap;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.VmIdentifier;

import java.net.URISyntaxException;

public class JVMDinamicAttacher extends Tool {

    public void run() {
        CollectedHeap heap = VM.getVM().getUniverse().heap();
        final PSOldGen oldGen = ((ParallelScavengeHeap) heap).oldGen();

        Klass klass = VM.getVM().getSystemDictionary().find("", null, null);

        VM.getVM().getObjectHeap().iterateObjectsOfKlass(new DefaultHeapVisitor() {
            @Override
            public boolean doObj(Oop oop) {
                if (oldGen.isIn(oop.getHandle())) {
                    oop.printValue();
                    System.out.println();
                }
                return false;
            }
        }, klass);
    }

    private void print(TypeArray array) {
        long length = array.getLength();
        for (long i = 0; i < length; i++) {
            System.out.printf("%02x", array.getByteAt(i));
        }
        System.out.println();
    }

    public void attach(String projName) throws MonitorException, URISyntaxException {
        MonitoredHost host = MonitoredHost.getMonitoredHost((String) null);
        Integer proccesId = null;
        for (Integer vmId : host.activeVms()) {
            MonitoredVm vm = host.getMonitoredVm(new VmIdentifier(vmId.toString()));
            String javaCommand = (String) vm.findByName("sun.rt.javaCommand").getValue();
            if (projName.equals(javaCommand)){
                proccesId = vmId;
                break;
            }
        }
        if(proccesId != null) {
            new JVMDinamicAttacher().execute(new String[]{proccesId.toString()});
        } else {
            throw new IllegalArgumentException("Такого процесса не существует");
        }
    }
}
