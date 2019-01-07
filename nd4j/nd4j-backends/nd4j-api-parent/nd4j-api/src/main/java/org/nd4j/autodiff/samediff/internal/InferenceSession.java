package org.nd4j.autodiff.samediff.internal;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.samediff.VariableType;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.*;
import org.nd4j.linalg.api.ops.impl.controlflow.If;
import org.nd4j.linalg.api.ops.impl.controlflow.While;
import org.nd4j.linalg.api.ops.impl.controlflow.compat.*;
import org.nd4j.linalg.api.ops.impl.transforms.same.Identity;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;

@Slf4j
public class InferenceSession extends AbstractSession<INDArray,DifferentialFunction> {

    public InferenceSession(@NonNull SameDiff sameDiff) {
        super(sameDiff);
    }

    @Override
    public INDArray[] getOutputs(DifferentialFunction op, FrameIter outputFrameIter, Set<VarId> opInputs, Set<String> constAndPhInputs) {

        int totalInputs = (opInputs == null ? 0 : opInputs.size()) + (constAndPhInputs == null ? 0 : constAndPhInputs.size());

        if(op instanceof Identity ) {
            Identity i = (Identity) op;
            String[] argNames = i.argNames();
            Preconditions.checkState(argNames.length == 1, "Expected only 1 arg name in identity op, got %s", argNames);
            VarId vid = newVarId(argNames[0], outputFrameIter);
            return new INDArray[]{nodeOutputs.get(vid)};

        } else if(op instanceof Switch) {
            Switch s = (Switch) op;
            String[] argNames = s.argNames();       //Order: input, boolean array
            VarId vidPredicate = newVarId(argNames[1], outputFrameIter);
            INDArray predicate = this.nodeOutputs.get(vidPredicate);
            Preconditions.checkState(predicate.isScalar() && predicate.dataType() == DataType.BOOL, "Expected boolean predicate: got %ndSInfo", predicate);
            VarId vid = newVarId(argNames[0], outputFrameIter);
            if (predicate.getDouble(0) == 0.0) {
                return new INDArray[]{this.nodeOutputs.get(vid), null};
            } else {
                return new INDArray[]{null, this.nodeOutputs.get(vid)};
            }
        } else if(op instanceof Enter) {
            //Enter op: forwards input to specified execution frame
            Enter e = (Enter)op;
            String frame = e.getFrameName();
            String[] input = e.argNames();
            Preconditions.checkState(input.length == 1, "Expected only 1 arg name for enter op: got %s", input);
            Preconditions.checkState(totalInputs == 1, "Expected exactly 1 op input, got %s+%s", opInputs, constAndPhInputs);

            VarId inputVarId;
            if(opInputs == null || opInputs.size() == 0){
                //Constant or placeholder
                inputVarId = new VarId(constAndPhInputs.iterator().next(), OUTER_FRAME, 0);
            } else {
                inputVarId = opInputs.iterator().next();
            }
            INDArray enterInput = this.nodeOutputs.get(inputVarId);

            Preconditions.checkNotNull(enterInput, "Could not get enter op input: output variable %s - %s", e.outputVariablesNames(), outputFrameIter);
            return new INDArray[]{enterInput};
        } else if(op instanceof Exit) {
            //Exit node forwards input to parent frame

            VarId inputVarId;
            if(opInputs == null || opInputs.size() == 0){
                //Constant or placeholder
                inputVarId = new VarId(constAndPhInputs.iterator().next(), OUTER_FRAME, 0);
            } else {
                inputVarId = opInputs.iterator().next();
            }
            INDArray exitInput = this.nodeOutputs.get(inputVarId);
            return new INDArray[]{exitInput};
        } else if(op instanceof NextIteration){
            //NextIteration op: forwards its single input to the output of the current frame, but increments the iteration number
            Preconditions.checkState(totalInputs == 1, "Expected exactly 1 op input for NextIteration: got %s+%s", opInputs, constAndPhInputs);
            VarId in = opInputs.iterator().next();
            Preconditions.checkState(outputFrameIter.getFrame().equals(in.getFrame()), "Expected same frame for NextIteration input vs. output:" +
                    " got input %s, output %s", in, outputFrameIter);
            Preconditions.checkState(outputFrameIter.getIteration() == in.getIteration()+1, "Expected output iteration for NextIteration output to" +
                    " be 1 larger than the input iteration. Input: %s, output %s", in, outputFrameIter);

            INDArray inArr = this.nodeOutputs.get(in);
            return new INDArray[]{inArr};
        } else if(op instanceof If) {
            If i = (If) op;
            String[] argNames = i.argNames();       //Order should be: [boolean], true, false


            throw new UnsupportedOperationException("Execution not yet implemented for: " + op.getClass().getName());
        } else if(op instanceof Merge) {
            //Merge avairable for forward pass when any of its inputs are available. When multiple are available, behaviour
            // is undefined
            Merge m = (Merge) op;
            String[] in = sameDiff.getInputsForFunction(op);
            for (String s : in) {
                VarId vid = newVarId(s, outputFrameIter);
                if (nodeOutputs.containsKey(vid)) {
                    log.trace("Returning input \"{}\" for merge node \"{}\"", m.getOwnName(), s);
                    return new INDArray[]{nodeOutputs.get(vid)};
                }
            }
            throw new IllegalStateException("Merge node " + m.getOwnName() + " has no available inputs (all inputs: " + Arrays.toString(in) +
                    ") - should not be executed at this point");
        } else if(op instanceof LoopCond) {
            //LoopCond just forwards scalar boolean to output
            LoopCond lc = (LoopCond) op;
            String[] argNames = lc.argNames();
            Preconditions.checkState(argNames.length == 1, "Expected only 1 arg name in LoopCond op, got %s", argNames);
            VarId vid = newVarId(argNames[0], outputFrameIter);
            INDArray arr = nodeOutputs.get(vid);
            Preconditions.checkNotNull(arr, "Input to LoopCond op must not be null");
            Preconditions.checkState(arr.isScalar() && arr.dataType() == DataType.BOOL, "LoopCond input must be a scalar boolean, got %ndShape");
            return new INDArray[]{arr};
        } else if(op instanceof CustomOp){
            CustomOp c = (CustomOp)op;
            Nd4j.getExecutioner().exec(c);
            return c.outputArguments();
        } else if(op instanceof Op) {
            Op o = (Op) op;
            Nd4j.getExecutioner().exec(o);
            return new INDArray[]{o.z()};
        } else {
            throw new UnsupportedOperationException("Execution not yet implemented for: " + op.getClass().getName());
        }
    }

    @Override
    public INDArray getConstantOrVariable(String variableName) {
        SDVariable v = sameDiff.getVariable(variableName);
        Preconditions.checkState(sameDiff.getVariable(variableName).isConstant() || v.getVariableType() == VariableType.VARIABLE,
                "Variable %s is not a constant", variableName);
        return sameDiff.getArrForVarName(variableName);
    }

    @Override
    public DifferentialFunction getAndParameterizeOp(String opName, FrameIter frameIter, Set<VarId> opInputs, Set<String> constAndPhInputs, Map<String,INDArray> placeholderValues) {

        DifferentialFunction df = sameDiff.getFunctionById(opName);

        //TODO We should clone these - probably - as we don't want them shared between threads/sessions!
        //But let's only clone them *once* and cache in inference session - not on every exec

        Preconditions.checkNotNull(df, "No differential function fond with name %s", opName);

        if(df instanceof LoopCond || df instanceof Enter || df instanceof Exit || df instanceof NextIteration ||
                df instanceof Merge || df instanceof Switch || df instanceof If || df instanceof While){
            return df;
        }

        //Infer the args based on the inputs (variable + frame + iteration)
        String[] argNames = df.argNames();
        int numArgs = (argNames == null ? 0 : argNames.length);
        int numNonConstIns = (opInputs == null ? 0 : opInputs.size());
        int numConstPhIns = (constAndPhInputs == null ? 0 : constAndPhInputs.size());
        if(numArgs != (numNonConstIns + numConstPhIns)){
            if(numArgs > 1){
                //Might be due to repeated inputs
                Set<String> uniqueArgNames = new HashSet<>();
                Collections.addAll(uniqueArgNames, argNames);
                Preconditions.checkState(uniqueArgNames.size() == (numNonConstIns + numConstPhIns),
                        "Different number of arg names as op inputs for op %s (%s): arg names %s vs. op inputs %s+%s", df.getClass().getSimpleName(),
                        opName, uniqueArgNames, opInputs, constAndPhInputs);
            } else {
                Preconditions.checkState(numArgs == (numNonConstIns + numConstPhIns),
                        "Different number of arg names as op inputs for op %s (%s): arg names %s vs. op inputs %s+%s", df.getClass().getSimpleName(),
                        opName, argNames, opInputs, constAndPhInputs);
            }
        }

        INDArray[] args = null;
        if(argNames != null && argNames.length > 0) {
            args = new INDArray[argNames.length];
            int i = 0;
            for(String s : argNames){
                SDVariable v = sameDiff.getVariable(s);
                if(v.isConstant()) {
                    args[i] = v.getArr();
                } else if(v.isPlaceHolder()){
                    Preconditions.checkState(placeholderValues != null && placeholderValues.containsKey(s), "No array provided for placeholder %s");
                    args[i] = placeholderValues.get(s);
                } else {
                    for(VarId vid : opInputs){
                        if(vid.getVariable().equals(s)){
                            args[i] = this.nodeOutputs.get(vid);
                            break;
                        }
                    }
                }
                Preconditions.checkNotNull(args[i], "Could not parameterize op %s: array %s (variable %s) is null", opName, i, v.getVarName());
                i++;
            }

        }

        //Set the op inputs and output arguments
        //Note that when we are in a loop (and non-first iteration), we want to allocate new arrays even if shapes are
        // ok: this is because we need the values in past iterations for backprop (potentially)
        //TODO let's find a way to use in-place modification for loops where possible to reduce memory requirements
        boolean isLoop = !frameIter.getFrame().equals(OUTER_FRAME) && frameIter.getIteration() > 0;

        if(df instanceof CustomOp){
            DynamicCustomOp customOp = (DynamicCustomOp) df;
            if(args != null) {
                customOp.setInputArguments(args);
            }

            df.resolvePropertiesFromSameDiffBeforeExecution();
            List<LongShapeDescriptor> outShape = customOp.calculateOutputShape();
            Preconditions.checkState(outShape != null && outShape.size() > 0, "Failed to calculate output shapes for op %s (%s) - no shapes were returned by calculateOutputShape()", customOp.opName(), customOp.getOwnName());
            String[] outNames = df.outputVariablesNames();
            for( int i=0; i<outShape.size(); i++ ){
                INDArray currOutput = (customOp.numOutputArguments() <= i ? null : customOp.getOutputArgument(i));
                LongShapeDescriptor reqShape = outShape.get(i);

                //Issue: many ops have multiple valid output datatypes, and output shape calc can't at present know which: https://github.com/deeplearning4j/deeplearning4j/issues/6872
                //As a workaround, we'll use the output variable datatype instead.
                DataType dt = sameDiff.getVariable(outNames[i]).dataType();
                if(dt != reqShape.dataType()){
                    reqShape = reqShape.asDataType(dt);
                }

                if(currOutput == null || !currOutput.shapeDescriptor().equals(reqShape) || isLoop){
                    INDArray out = Nd4j.create(reqShape, false);
                    customOp.setOutputArgument(i, out);
                }
            }

        } else if(df instanceof Op){
            Op op = (Op) df;

            boolean axisArg = false;
            if(op instanceof ReduceOp && ((ReduceOp) op).getOpType() != Op.Type.REDUCE3 && df.argNames().length == 2){
                //2nd input should be treated as integer axis arg...
                SDVariable axisArgVar = df.arg(1);
                String n = axisArgVar.getVarName();
                Preconditions.checkState(axisArgVar.dataType().isIntType(), "Legacy op %s input 1 (axis) was expected to be an integer type, is %s", df.getClass(), axisArgVar.dataType());

                INDArray arr = null;
                if(axisArgVar.isConstant() || axisArgVar.getVariableType() == VariableType.VARIABLE){
                    arr = getConstantOrVariable(n);
                } else if(axisArgVar.isPlaceHolder()){
                    arr = placeholderValues.get(n);
                } else {
                    //Must be array type
                    for(VarId vid : opInputs){
                        if(vid.getVariable().equals(n)){
                            arr = get(vid.getVariable(), vid.getFrame(), vid.getIteration());
                            break;
                        }
                    }
                }
                Preconditions.checkState(arr != null, "Could not get axis argument for op %s: %s", df.getOwnName(), df.getClass());
                if(!arr.isEmpty()){
                    int[] axis = arr.toIntVector();
                    df.setDimensions(axis);
                } else {
                    df.setDimensions(null);
                }
                axisArg = true;
            }

            if(args != null && args.length > 0){
                op.setX(args[0]);
                if (args.length == 2 && !axisArg)
                    op.setY(args[1]);
            }


            //Check output shape; allocate a new Z if required
            //For example, if minibatch size has changed since last op execution
            List<LongShapeDescriptor> outputShape = ((BaseOp)op).calculateOutputShape();
            Preconditions.checkState(outputShape != null && outputShape.size() == 1, "Could not calculate output shape for op: %s", op.getClass());
            INDArray z = op.z();
            if(z == null || !outputShape.get(0).equals(z.shapeDescriptor()) || isLoop){
                if(log.isTraceEnabled()){
                    log.trace("Existing op result (z) array shape for op {} was {}, allocating new array of shape {}",
                            op.getClass().getSimpleName(), Arrays.toString(z.shape()), outputShape.get(0).toString());
                }
                z = Nd4j.create(outputShape.get(0), false);
                op.setZ(z);
            }
            df.resolvePropertiesFromSameDiffBeforeExecution();
        }

        return df;
    }
}
