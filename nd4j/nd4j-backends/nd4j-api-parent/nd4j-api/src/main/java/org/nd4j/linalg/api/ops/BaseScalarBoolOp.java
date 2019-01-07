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

package org.nd4j.linalg.api.ops;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Base scalar operation
 *
 * @author Adam Gibson
 */
@Slf4j
public abstract class BaseScalarBoolOp extends BaseOp implements ScalarOp {
    public BaseScalarBoolOp() {}

    public BaseScalarBoolOp(INDArray x, INDArray y, INDArray z, long n, Number num) {
        super(x, y, z, n);
        this.scalarValue = Nd4j.scalar(x.dataType(), num);

        init(x, y, z, n);
    }

    public BaseScalarBoolOp(INDArray x, Number num) {
        super(x);
        this.scalarValue = Nd4j.scalar(x.dataType(), num);
        init(x, y, z, n);

    }
    public BaseScalarBoolOp(INDArray x, INDArray z, Number set) {
        super(x, null, z, x.length());
        this.scalarValue= Nd4j.scalar(x.dataType(), set);
    }




    public BaseScalarBoolOp(SameDiff sameDiff, SDVariable i_v, Number scalar) {
        this(sameDiff,i_v,scalar,false,null);
    }

    public BaseScalarBoolOp(SameDiff sameDiff, SDVariable i_v, Number scalar, boolean inPlace) {
        this(sameDiff,i_v,scalar,inPlace,null);
    }

    public BaseScalarBoolOp(SameDiff sameDiff,
                            SDVariable i_v,
                            Number scalar,
                            boolean inPlace,
                            Object[] extraArgs) {
        super(sameDiff,inPlace,extraArgs);
        this.scalarValue = Nd4j.scalar(i_v.dataType(), scalar);
        if (i_v != null) {
            this.xVertexId = i_v.getVarName();
            sameDiff.addArgsFor(new String[]{xVertexId},this);
            if(Shape.isPlaceholderShape(i_v.getShape())) {
                sameDiff.addPropertyToResolve(this,i_v.getVarName());
            }
            f().validateDifferentialFunctionsameDiff(i_v);
        } else {
            throw new IllegalArgumentException("Input not null variable.");
        }

    }


    public BaseScalarBoolOp(SameDiff sameDiff,
                            SDVariable i_v,
                            Number scalar,
                            Object[] extraArgs) {
        this(sameDiff,i_v,scalar,false,extraArgs);
    }



    @Override
    public INDArray z() {
        if(z == null) {
            if(sameDiff != null) {
                this.z = outputVariables()[0].getArr();
                if(this.z == null) {
                    val var = outputVariables()[0];
                    if(var.getShape() != null)
                        this. z = var.storeAndAllocateNewArray();
                    else {
                        val argsShape = args()[0].getShape();
                        if(argsShape != null) {
                            sameDiff.putShapeForVarName(var.getVarName(),argsShape);
                            this. z = var.storeAndAllocateNewArray();
                        }
                    }
                }
            }
        }

        return z;
    }


    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {
        List<LongShapeDescriptor> ret = new ArrayList<>(1);
        ret.add(LongShapeDescriptor.fromShape(arg().getShape(), Shape.pickPairwiseDataType(larg().dataType(), scalarValue.dataType())));
        return ret;
    }

    @Override
    public Type opType() {
        return Type.SCALAR_BOOL;
    }

    @Override
    public void setScalar(Number scalar) {
        this.scalarValue = Nd4j.scalar(scalar);
    }

    @Override
    public INDArray scalar() {
        if(scalarValue == null && y() != null && y().isScalar())
            return y();
        return scalarValue;
    }


    @Override
    public int[] getDimension() {
        return dimensions;
    }

    @Override
    public void setDimension(int... dimension) {
        defineDimensions(dimension);
    }

    @Override
    public boolean validateDataTypes(boolean experimentalMode) {
        Preconditions.checkArgument(z().isB(), "Op.Z must have floating point type, since one of operands is floating point." +
                " op.z.datatype=" + z().dataType());

        return true;
    }

    @Override
    public Type getOpType() {
        return Type.SCALAR_BOOL;
    }
}
