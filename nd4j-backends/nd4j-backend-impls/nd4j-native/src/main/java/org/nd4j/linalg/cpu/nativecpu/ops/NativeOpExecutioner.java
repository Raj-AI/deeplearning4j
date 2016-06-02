package org.nd4j.linalg.cpu.nativecpu.ops;


import org.apache.commons.math3.util.Pair;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.complex.IComplexNDArray;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.*;
import org.nd4j.linalg.api.ops.executioner.DefaultOpExecutioner;
import org.nd4j.linalg.api.ops.impl.accum.Variance;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.cache.ConstantHandler;
import org.nd4j.linalg.cpu.nativecpu.CpuTADManager;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.util.ArrayUtil;
import org.nd4j.nativeblas.NativeOps;

import java.util.Arrays;


/**
 *
 * Native operation
 * executioner in c++
 *
 * @author Adam Gibson
 */

public class NativeOpExecutioner extends DefaultOpExecutioner {
    private NativeOps loop = new NativeOps();
    private ConstantHandler constantHandler = Nd4j.getConstantHandler();
    private CpuTADManager tadManager = new CpuTADManager();

    public NativeOpExecutioner() {
        tadManager.init(loop, constantHandler);
    }

    @Override
    public Op exec(Op op) {
        if(op instanceof ScalarOp) {
            ScalarOp s = (ScalarOp) op;
            exec(s);
        }
        else if(op instanceof TransformOp) {
            TransformOp t = (TransformOp) op;
            exec(t);
        }
        else if(op instanceof Accumulation) {
            Accumulation ac = (Accumulation) op;
            exec(ac);
        }
        else if(op instanceof IndexAccumulation) {
            IndexAccumulation iac = (IndexAccumulation) op;
            exec(iac);  //Currently using DefaultOpExecutioner
        }
        else if(op instanceof BroadcastOp) {
            BroadcastOp broadcastOp = (BroadcastOp) op;
            exec(broadcastOp,broadcastOp.getDimension());
        }

        return op;
    }


    @Override
    public INDArray exec(IndexAccumulation op, int... dimension) {
        Arrays.sort(dimension);
        for(int i = 0; i < dimension.length; i++) {
            if(dimension[i] < 0)
                dimension[i] += op.x().rank();
        }
        //do op along all dimensions
        if (dimension.length == op.x().rank())
            dimension = new int[]{Integer.MAX_VALUE};



        int[] retShape = Shape.wholeArrayDimension(dimension) ? new int[] {1,1} : ArrayUtil.removeIndex(op.x().shape(), dimension);
        if(op.x().isVector() && op.x().length() == ArrayUtil.prod(retShape))
            return op.x();


        //ensure vector is proper shape
        if (retShape.length == 1) {
            if (dimension[0] == 0)
                retShape = new int[]{1, retShape[0]};
            else
                retShape = new int[]{retShape[0], 1};
        } else if (retShape.length == 0) {
            retShape = new int[]{1, 1};
        }

        INDArray ret = Nd4j.valueArrayOf(retShape,op.zeroDouble());
        op.setZ(ret);
        //do op along all dimensions
        if (dimension.length == op.x().rank())
            dimension = new int[]{Integer.MAX_VALUE};


        long dimensionAddress = constantHandler.getConstantBuffer(dimension).address();

        Pair<DataBuffer, DataBuffer> tadBuffers = tadManager.getTADOnlyShapeInfo(op.x(), dimension);

        long hostTadShapeInfo = tadBuffers.getFirst().address();

        DataBuffer offsets = tadBuffers.getSecond();
        long hostTadOffsets = offsets == null ? 0 : offsets.address();

        long[] dummy = new long[]{
                hostTadShapeInfo,
                hostTadOffsets,
        };

        long x = op.x().data().address();
        long z = op.z().data().address();

        if(op.x().data().dataType() == DataBuffer.Type.DOUBLE) {
            loop.execIndexReduceDouble(
                    dummy,
                    op.opNum(),
                    x,
                    op.x().shapeInfoDataBuffer().address(),
                    getAddressForExtraArgs(op),
                    z,
                    op.z().shapeInfoDataBuffer().address(),
                    dimensionAddress, dimension.length);

        }
        else {
            loop.execIndexReduceFloat(
                    dummy,
                    op.opNum(),
                    op.x().data().address(),
                    op.x().shapeInfoDataBuffer().address(),
                    getAddressForExtraArgs(op),
                    op.z().data().address(),
                    op.z().shapeInfoDataBuffer().address(),
                    dimensionAddress, dimension.length);

        }
        return op.z();
    }



    @Override
    public INDArray exec(Accumulation op, int... dimension) {
        Arrays.sort(dimension);

        for(int i = 0; i < dimension.length; i++) {
            if(dimension[i] < 0)
                dimension[i] += op.x().rank();
        }
        //do op along all dimensions
        if (dimension.length == op.x().rank())
            dimension = new int[]{Integer.MAX_VALUE};


        int[] retShape = Shape.wholeArrayDimension(dimension) ? new int[] {1,1} : ArrayUtil.removeIndex(op.x().shape(), dimension);
        //ensure vector is proper shape
        if (retShape.length == 1) {
            if (dimension[0] == 0)
                retShape = new int[]{1, retShape[0]};
            else
                retShape = new int[]{retShape[0], 1};
        } else if (retShape.length == 0) {
            retShape = new int[]{1, 1};
        }

        if(op.x().isVector() && op.x().length() == ArrayUtil.prod(retShape))
            return op.noOp();

        INDArray ret = Nd4j.valueArrayOf(retShape,op.zeroDouble());
        op.setZ(ret);


        Pair<DataBuffer, DataBuffer> tadBuffers = tadManager.getTADOnlyShapeInfo(op.x(), dimension);

        long hostTadShapeInfo = tadBuffers.getFirst().address();

        DataBuffer offsets = tadBuffers.getSecond();
        long hostTadOffsets = offsets == null ? 0 : offsets.address();

        long[] dummy = new long[]{
                hostTadShapeInfo,
                hostTadOffsets,
        };

        long dimensionAddress = constantHandler.getConstantBuffer(dimension).address();

        if(op.x().data().dataType() == DataBuffer.Type.DOUBLE) {
            if(op instanceof Variance) {
                if(ret.isScalar()) {
                    ret.putScalar(0,loop.execSummaryStatsScalarDouble(
                            dummy,
                            op.opNum()
                            , op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op), true));
                }
                else {
                    Variance var = (Variance) op;
                    loop.execSummaryStatsDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),dimensionAddress,dimension.length,
                            var.isBiasCorrected());
                }

            }

            else if(op.y() != null) {
                if(ret.isScalar()) {
                    ret.putScalar(0,loop.execReduce3ScalarDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op),
                            op.y().data().address(),
                            op.y().shapeInfoDataBuffer().address()));
                }
                else {
                    loop.execReduce3Double(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op),
                            op.y().data().address(),
                            op.y().shapeInfoDataBuffer().address(),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),
                            dimensionAddress, dimension.length);
                }

            }
            else {
                if(ret.isScalar()) {
                    ret.putScalar(0,loop.execReduceScalarDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op)));
                }
                else {
                    loop.execReduceDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(), getAddressForExtraArgs(op),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),
                            dimensionAddress, dimension.length);
                }

            }
        }
        else {
            if(op instanceof Variance) {
                Variance variance = (Variance) op;
                if(ret.isScalar()) {
                    ret.putScalar(0,loop.execSummaryStatsScalarFloat(
                            dummy,
                            op.opNum()
                            , op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op),variance.isBiasCorrected()));
                }
                else {
                    loop.execSummaryStatsFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),getAddressForExtraArgs(op),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),
                            dimensionAddress, dimension.length,variance.isBiasCorrected());
                }

            }

            else if(op.y() != null) {
                if(ret.isScalar()) {
                    ret.putScalar(0,loop.execReduce3ScalarFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op),
                            op.y().data().address(),
                            op.y().shapeInfoDataBuffer().address()));
                }
                else {
                    loop.execReduce3Float(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),getAddressForExtraArgs(op),
                            op.y().data().address(),
                            op.y().shapeInfoDataBuffer().address(),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),
                            dimensionAddress, dimension.length);
                }

            }
            else {
                if(ret.isScalar()) {
                    ret.putScalar(0,loop.execReduceScalarFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op)));
                }
                else {
                    loop.execReduceFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),
                            dimensionAddress, dimension.length);
                }

            }
        }

        return ret;
    }

    private void exec(ScalarOp op) {
        if(op.x() instanceof IComplexNDArray || executionMode() == ExecutionMode.JAVA) {
            super.exec(op);
        }
        else {
            long[] dummy = new long[1];
            if(op.x().data().dataType() == DataBuffer.Type.DOUBLE) {
                if(op.x(). elementWiseStride() >= 1 && !op.isExecSpecial() && op.z(). elementWiseStride() >= 1 && !op.isExecSpecial()) {
                    loop.execScalarDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().elementWiseStride(),
                            op.z().data().address(),
                            op.z().elementWiseStride(),
                            op.scalar().doubleValue(),
                            getAddressForExtraArgs(op),
                            op.n());
                }
                else
                    loop.execScalarDouble(
                            dummy,
                            op.opNum()
                            , op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),
                            op.scalar().doubleValue(),
                            getAddressForExtraArgs(op));
            }
            else {
                if(op.x(). elementWiseStride() >= 1 && !op.isExecSpecial() && op.z(). elementWiseStride() >= 1 && !op.isExecSpecial()) {
                    loop.execScalarFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address(),
                            op.x().elementWiseStride(),
                            op.z().data().address(),
                            op.z().elementWiseStride(),
                            op.scalar().floatValue(),
                            getAddressForExtraArgs(op),
                            op.n());
                }
                else
                    loop.execScalarFloat(
                            dummy,
                            op.opNum()
                            , op.x().data().address(),
                            op.x().shapeInfoDataBuffer().address(),
                            op.z().data().address(),
                            op.z().shapeInfoDataBuffer().address(),
                            op.scalar().floatValue(),
                            getAddressForExtraArgs(op));

            }
        }
    }

    private long getAddressForExtraArgs(Op op) {
        if(op.extraArgs() != null)
            return op.extraArgsDataBuff().address();
        return 0;
    }

    private void exec(TransformOp op) {
            long[] dummy = new long[1];
            if(op.x().data().dataType() == DataBuffer.Type.DOUBLE) {
                if(op.y() != null) {
                    if(op.x().elementWiseStride() >=1 && op.y(). elementWiseStride() >= 1 && op.x().elementWiseStride() == op.y(). elementWiseStride()  && !op.isExecSpecial() && op.x().ordering() == op.y().ordering() && op.x().ordering() == op.z().ordering()) {
                        loop.execPairwiseTransformDouble(
                                dummy,
                                op.opNum(),
                                op.x().data().address(),
                                op.x().elementWiseStride(),
                                op.y().data().address(),
                                op.y().elementWiseStride(),
                                op.z().data().address(),
                                op.z().elementWiseStride(),
                                getAddressForExtraArgs(op),
                                op.n());

                    }
                    else {
                        loop.execPairwiseTransformDouble(
                                dummy,
                                op.opNum(),
                                op.x().data().address(),
                                op.x().shapeInfoDataBuffer().address(),
                                op.y().data().address(),
                                op.y().shapeInfoDataBuffer().address(),
                                op.z().data().address(),
                                op.z().shapeInfoDataBuffer().address(),
                                getAddressForExtraArgs(op));
                    }

                }
                else {
                    if(op.x(). elementWiseStride() >= 1 && !op.isExecSpecial() && !op.isExecSpecial() && op.x().ordering() == op.z().ordering()) {
                        loop.execTransformDouble(
                                dummy,
                                op.opNum(),
                                op.x().data().address(),
                                op.x().elementWiseStride(),
                                op.z().data().address(),
                                op.z().elementWiseStride(),
                                getAddressForExtraArgs(op), op.n());
                    }
                    else {
                        loop.execTransformDouble(
                                dummy,
                                op.opNum(),
                                op.x().data().address(),
                                op.x().shapeInfoDataBuffer().address(),
                                op.z().data().address(),
                                op.z().shapeInfoDataBuffer().address(),
                                getAddressForExtraArgs(op));
                    }

                }
            }
            else {
                if(op.y() != null) {
                    if(op.x().elementWiseStride() >=1 && op.y(). elementWiseStride() >= 1 && op.x().elementWiseStride() == op.y(). elementWiseStride() && !op.isExecSpecial() && op.x().ordering() == op.y().ordering()) {
                        loop.execPairwiseTransformFloat
                                (dummy,op.opNum(),
                                        op.x().data().address(),
                                        op.x().elementWiseStride(),
                                        op.y().data().address(),
                                        op.y().elementWiseStride(),
                                        op.z().data().address(),
                                        op.z().elementWiseStride(),
                                        getAddressForExtraArgs(op),
                                        op.n());

                    }
                    else {
                        loop.execPairwiseTransformFloat(
                                dummy,
                                op.opNum(),
                                op.x().data().address(),
                                op.x().shapeInfoDataBuffer().address(),
                                op.y().data().address(),
                                op.y().shapeInfoDataBuffer().address(),
                                op.z().data().address(),
                                op.z().shapeInfoDataBuffer().address(),
                                getAddressForExtraArgs(op));
                    }

                }
                else {
                    if(op.x(). elementWiseStride() >= 1 && !op.isExecSpecial() && op.x().ordering() == op.z().ordering()) {
                        loop.execTransformFloat(dummy,op.opNum(),
                                op.x().data().address(),
                                op.x().elementWiseStride(),
                                op.z().data().address(),
                                op.z().elementWiseStride(),
                                getAddressForExtraArgs(op), op.n());
                    }
                    else {
                        loop.execTransformFloat(
                                dummy,
                                op.opNum(),
                                op.x().data().address(),
                                op.x().shapeInfoDataBuffer().address(),
                                op.z().data().address(),
                                op.z().shapeInfoDataBuffer().address(),
                                getAddressForExtraArgs(op));
                    }

                }
            }

    }

    @Override
    public INDArray exec(BroadcastOp op,int...dimension) {
        Arrays.sort(dimension);

        Pair<DataBuffer, DataBuffer> tadBuffers = tadManager.getTADOnlyShapeInfo(op.x(), dimension);

        long hostTadShapeInfo = tadBuffers.getFirst().address();
        long hostTadOffsets = tadBuffers.getSecond().address();

        long[] dummy = new long[]{
                hostTadShapeInfo,
                hostTadOffsets,
        };

        long dimensionAddress = constantHandler.getConstantBuffer(dimension).address();

        if(op.x().data().dataType() == DataBuffer.Type.DOUBLE) {
            loop.execBroadcastDouble(dummy,op.opNum(),
                    op.x().data().address()
                    ,op.x().shapeInfoDataBuffer().address(),
                    op.y().data().address(), op.y().shapeInfoDataBuffer().address()
                    , op.z().data().address(), op.z().shapeInfoDataBuffer().address(),
                    dimensionAddress, dimension.length);
        }
        else {
            loop.execBroadcastFloat(dummy,op.opNum(),
                    op.x().data().address()
                    ,op.x().shapeInfoDataBuffer().address(),
                    op.y().data().address(),
                    op.y().shapeInfoDataBuffer().address()
                    , op.z().data().address(),
                    op.z().shapeInfoDataBuffer().address(),
                    dimensionAddress, dimension.length);
        }

        return op.z();
    }

    private void exec(IndexAccumulation op) {
        if(op.x() instanceof IComplexNDArray || executionMode() == ExecutionMode.JAVA) {
            super.exec(op);

        }
        else {
            long[] dummy = new long[1];
            if(op.x().data().dataType() == DataBuffer.Type.DOUBLE) {
                op.setFinalResult((int) loop.execIndexReduceScalarDouble(
                        dummy,
                        op.opNum(),
                        op.x().data().address()
                        ,op.x().shapeInfoDataBuffer().address(), getAddressForExtraArgs(op)));

            }
            else {
                op.setFinalResult((int) loop.execIndexReduceScalarFloat(
                        dummy,
                        op.opNum(),
                        op.x().data().address()
                        ,op.x().shapeInfoDataBuffer().address(),
                        getAddressForExtraArgs(op)));
            }

        }
    }

    private void exec(Accumulation op) {
        if(op.x() instanceof IComplexNDArray || executionMode() == ExecutionMode.JAVA) {
            super.exec(op);

        }
        else {
            long[] dummy = new long[1];
            if(op.x().data().dataType() == DataBuffer.Type.DOUBLE) {
                if(op instanceof Variance) {
                    op.setFinalResult(loop.execSummaryStatsScalarDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address()
                            ,op.x().shapeInfoDataBuffer().address(), getAddressForExtraArgs(op), true));
                }
                else if(op.y() != null) {
                    op.setFinalResult(loop.execReduce3ScalarDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address()
                            ,op.x().shapeInfoDataBuffer().address(),getAddressForExtraArgs(op),
                            op.y().data().address(), op.y().shapeInfoDataBuffer().address()));
                }
                else {
                    op.setFinalResult(loop.execReduceScalarDouble(
                            dummy,
                            op.opNum(),
                            op.x().data().address()
                            ,op.x().shapeInfoDataBuffer().address(), getAddressForExtraArgs(op)));
                }
            }
            else {
                if(op instanceof Variance) {
                    Variance variance = (Variance) op;
                    op.setFinalResult(loop.execSummaryStatsScalarFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address()
                            ,op.x().shapeInfoDataBuffer().address(),  getAddressForExtraArgs(op),variance.isBiasCorrected()));
                }
                else if(op.y() != null) {
                    op.setFinalResult(loop.execReduce3ScalarFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address()
                            ,op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op),
                            op.y().data().address(),
                            op.y().shapeInfoDataBuffer().address()));
                }
                else {
                    op.setFinalResult(loop.execReduceScalarFloat(
                            dummy,
                            op.opNum(),
                            op.x().data().address()
                            ,op.x().shapeInfoDataBuffer().address(),
                            getAddressForExtraArgs(op)));
                }
            }
        }
    }
}
