package org.deeplearning4j.nn.layers.samediff;

import org.nd4j.autodiff.samediff.internal.memory.AbstractMemoryMgr;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.memory.conf.WorkspaceConfiguration;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.factory.Nd4j;

/**
 * A SameDiff {@link org.nd4j.autodiff.samediff.internal.SessionMemMgr} that uses DL4J workspaces for memory management.
 * Any op outputs are allocated in the output workspace if they are returned to the layer; otherwise they are placed in
 * the DL4J working memory workspace
 *
 * @author Alex Black
 */
public class DL4JSameDiffMemoryMgr extends AbstractMemoryMgr {

    private final String workingMemoryWs;
    private final String outputWs;
    private final WorkspaceConfiguration confWorking;
    private final WorkspaceConfiguration confOutput;

    //Note: if the working memory or output workspace names are null -> detached memory
    public DL4JSameDiffMemoryMgr(String workingMemoryWs, String outputWs, WorkspaceConfiguration confWorking,
                                 WorkspaceConfiguration confOutput){
        this.workingMemoryWs = workingMemoryWs;
        this.outputWs = outputWs;
        this.confWorking = confWorking;
        this.confOutput = confOutput;
    }


    @Override
    public INDArray allocate(boolean detached, DataType dataType, long... shape) {
        String wsName = detached ? outputWs : workingMemoryWs;
        WorkspaceConfiguration wsConf = detached ? confOutput : confWorking;

        if(wsName == null){
            //Scoped out
            INDArray ret = Nd4j.createUninitializedDetached(dataType, shape);
            Preconditions.checkState(!ret.isAttached(), "Returned array should be detached");
            return ret;
        } else {
            MemoryWorkspace ws = Nd4j.getWorkspaceManager().getWorkspaceForCurrentThread(wsConf, wsName);
            try (MemoryWorkspace mw = ws.notifyScopeBorrowed()) {
                return Nd4j.createUninitialized(dataType, shape);
            }
        }
    }

    @Override
    public INDArray allocate(boolean detached, LongShapeDescriptor descriptor) {
        return allocate(detached, descriptor.dataType(), descriptor.getShape());
    }

    @Override
    public void release(INDArray array) {
        //No-op - DL4J workspaces handles this
    }

    @Override
    public void close() {
        //No-op - DL4J workspaces handles this
    }
}
