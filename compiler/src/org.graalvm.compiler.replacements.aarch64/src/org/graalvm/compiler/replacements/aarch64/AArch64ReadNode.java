package org.graalvm.compiler.replacements.aarch64;

import jdk.vm.ci.aarch64.AArch64Kind;
import org.graalvm.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRGenerator;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

/**
 * AArch64-specific subclass of ReadNode that knows hwo to merge ZeroExtend and SignExtend into the read
 */

@NodeInfo
public class AArch64ReadNode extends ReadNode
{
    public static final NodeClass<AArch64ReadNode> TYPE = NodeClass.create(AArch64ReadNode.class);
    private final IntegerStamp accessStamp;
    private final boolean isSigned;

    public AArch64ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck,
                           FrameState stateBefore, IntegerStamp accessStamp, boolean isSigned) {
        super(TYPE, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
        this.accessStamp = accessStamp;
        this.isSigned = isSigned;
    }
    @Override
    public void generate(NodeLIRBuilderTool gen) {
        AArch64LIRGenerator lirgen = (AArch64LIRGenerator) gen.getLIRGeneratorTool();
        AArch64ArithmeticLIRGenerator arithgen = (AArch64ArithmeticLIRGenerator) lirgen.getArithmetic();
        AArch64Kind readKind = (AArch64Kind) lirgen.getLIRKind(accessStamp).getPlatformKind();
        int resultBits = ((IntegerStamp)stamp()).getBits();
        gen.setResult(this, arithgen.emitExtendMemory(isSigned, readKind, resultBits, (AArch64AddressValue) gen.operand(getAddress()), gen.state(this)));
    }

    /**
     * replace a ReadNode with an AArch64-specific variant
     * which knows how to merge a downstream zero or sign
     * extend into the read operation
     *
     * @param readNode
     * @return the replacement node
     */
    public static void replace(ReadNode readNode) {
        assert readNode.getUsageCount() == 1;
        assert readNode.getUsageAt(0) instanceof ZeroExtendNode || readNode.getUsageAt(0) instanceof SignExtendNode;

        ValueNode usage =  (ValueNode) readNode.getUsageAt(0);
        boolean isSigned = usage instanceof SignExtendNode;
        IntegerStamp accessStamp = ((IntegerStamp)readNode.getAccessStamp());

        AddressNode address = readNode.getAddress();
        LocationIdentity location = readNode.getLocationIdentity();
        Stamp stamp = usage.stamp();
        GuardingNode guard = readNode.getGuard();
        BarrierType barrierType = readNode.getBarrierType();
        boolean nullCheck = readNode.getNullCheck();
        FrameState stateBefore = readNode.stateBefore();
        AArch64ReadNode clone = new  AArch64ReadNode(address, location, stamp, guard, barrierType, nullCheck, stateBefore, accessStamp, isSigned);
        StructuredGraph graph = readNode.graph();
        graph.add(clone);
        // splice out the extend node
        usage.replaceAtUsagesAndDelete(readNode);
        // swap the clone for the read
        graph.replaceFixedWithFixed(readNode, clone);
    }
}
