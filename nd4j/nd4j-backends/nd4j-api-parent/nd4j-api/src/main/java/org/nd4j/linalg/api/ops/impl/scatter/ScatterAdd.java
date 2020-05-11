/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.linalg.api.ops.impl.scatter;

import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Created by farizrahman4u on 3/23/18.
 */

public class ScatterAdd extends DynamicCustomOp {

    public ScatterAdd(SameDiff sameDiff, SDVariable ref, SDVariable indices, SDVariable updates) {
        super(null, sameDiff, new SDVariable[]{ref, indices, updates}, false);
    }

    public ScatterAdd(){}

    public ScatterAdd(@NonNull INDArray ref, @NonNull INDArray indices, @NonNull INDArray update){
        super(new INDArray[]{ref, indices, update}, null);
    }

    @Override
    public String opName() {
        return "scatter_add";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " + opName());
    }

    @Override
    public String tensorflowName() {
        return "ScatterAdd";
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        TFGraphMapper.initFunctionFromProperties(nodeDef.getOp(), this, attributesForNode, nodeDef, graph);

        if (nodeDef.containsAttr("use_locking")) {
            if (nodeDef.getAttrOrThrow("use_locking").getB() == true) {
                bArguments.add(true);
            } else {
                bArguments.add(false);
            }
        } else
            bArguments.add(false);
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> gradOut){
        //3 args: ref, indices, updates
        //For non-modified indices, input gradient (referenc) is same as output gradient
        //For modified indices, dL/dref = dL/dOut * dOut/dRef = dL/dOut * d(ref + update)/dRef = dL/dOut
        //And for updates, dL/du = dL/dOut * dOut/du = dL/dOut * d(ref + update)/du = dL/dOut -> gather op

        List<SDVariable> ret = new ArrayList<>(3);
        ret.add(gradOut.get(0));            //Reference array
        ret.add(sameDiff.zerosLike(arg(1)));  //Indices

        SDVariable gather = sameDiff.gather(gradOut.get(0), arg(1), 0);       //Updates
        ret.add(gather);

        return ret;
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == 3, "Expected exactly 3 input datatypes for %s, got %s", getClass(), inputDataTypes);
        Preconditions.checkState(inputDataTypes.get(0) == inputDataTypes.get(2), "Reference (input 0) and updates (input 2) must have exactly same data types, got %s and %s",
                inputDataTypes.get(0), inputDataTypes.get(2));
        return Collections.singletonList(inputDataTypes.get(0));
    }
}
