/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dx.dex.code;

import com.android.dx.rop.code.BasicBlock;
import com.android.dx.rop.code.BasicBlockList;
import com.android.dx.rop.code.RopMethod;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.type.Type;
import com.android.dx.rop.type.TypeList;
import com.android.dx.util.IntList;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Constructor of {@link CatchTable} instances from {@link RopMethod}
 * and associated data.
 */
public final class StdCatchBuilder implements CatchBuilder {
    /** the maximum range of a single catch handler, in code units */
    private static final int MAX_CATCH_RANGE = 65535;

    /** {@code non-null;} compact catch metadata captured from the ROP graph */
    private final CatchBlock[] blocks;

    /**
     * Constructs an instance. The ROP graph is inspected immediately and only
     * the small amount of metadata needed to build the catch table is kept.
     *
     * @param method {@code non-null;} method to build the list for
     * @param order {@code non-null;} block output order
     * @param addresses {@code non-null;} address objects for each block
     */
    public StdCatchBuilder(RopMethod method, int[] order,
            BlockAddresses addresses) {
        if (method == null) {
            throw new NullPointerException("method == null");
        }

        if (order == null) {
            throw new NullPointerException("order == null");
        }

        if (addresses == null) {
            throw new NullPointerException("addresses == null");
        }

        /*
         * Do not retain the RopMethod, BasicBlockList, output order, or the
         * full BlockAddresses arrays. A translated class can contain a very
         * large method, and keeping that graph alive until dex writing used
         * to make every later method compete with it for the Dalvik heap.
         * Capture only catch-relevant block data while the graph is available.
         * CodeAddress instances are also present in the output instruction
         * list, so retaining the handful used by catches does not retain the
         * ROP graph.
         */
        this.blocks = snapshot(method, order, addresses);
    }

    /** {@inheritDoc} */
    @Override
    public CatchTable build() {
        ArrayList<CatchTable.Entry> resultList =
                new ArrayList<CatchTable.Entry>(blocks.length);
        CatchBlock rangeStart = null;
        CatchBlock rangeEnd = null;

        for (CatchBlock block : blocks) {
            if (!block.hasHandlers()) {
                if (rangeStart != null) {
                    resultList.add(makeEntry(rangeStart, rangeEnd));
                    rangeStart = null;
                    rangeEnd = null;
                }
                continue;
            }

            if (rangeStart == null) {
                rangeStart = block;
                rangeEnd = block;
                continue;
            }

            if (rangeStart.sameHandlers(block)
                    && rangeIsValid(rangeStart, block)) {
                rangeEnd = block;
                continue;
            }

            resultList.add(makeEntry(rangeStart, rangeEnd));
            rangeStart = block;
            rangeEnd = block;
        }

        if (rangeStart != null) {
            resultList.add(makeEntry(rangeStart, rangeEnd));
        }

        int resultSz = resultList.size();
        if (resultSz == 0) {
            return CatchTable.EMPTY;
        }

        CatchTable result = new CatchTable(resultSz);
        for (int i = 0; i < resultSz; i++) {
            result.set(i, resultList.get(i));
        }
        result.setImmutable();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAnyCatches() {
        for (CatchBlock block : blocks) {
            if (block.hasHandlers()) {
                return true;
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override
    public HashSet<Type> getCatchTypes() {
        HashSet<Type> result = new HashSet<Type>(20);
        for (CatchBlock block : blocks) {
            for (Type type : block.exceptionTypes) {
                result.add(type);
            }
        }

        return result;
    }

    /**
     * Builds and returns the catch table for a given method.
     *
     * @param method {@code non-null;} method to build the list for
     * @param order {@code non-null;} block output order
     * @param addresses {@code non-null;} address objects for each block
     * @return {@code non-null;} the constructed table
     */
    public static CatchTable build(RopMethod method, int[] order,
            BlockAddresses addresses) {
        return new StdCatchBuilder(method, order, addresses).build();
    }

    /** Captures the catch-relevant parts of the ROP graph. */
    private static CatchBlock[] snapshot(RopMethod method, int[] order,
            BlockAddresses addresses) {
        BasicBlockList basicBlocks = method.getBlocks();
        ArrayList<CatchBlock> result = new ArrayList<CatchBlock>(order.length);

        for (int i = 0; i < order.length; i++) {
            BasicBlock block = basicBlocks.labelToBlock(order[i]);
            if (!block.canThrow()) {
                /* Blocks that cannot throw do not affect catch ranges. */
                continue;
            }

            TypeList catches = block.getLastInsn().getCatches();
            int catchSize = catches.size();
            if (catchSize == 0) {
                /* A throwing block without handlers terminates the range. */
                result.add(CatchBlock.BARRIER);
                continue;
            }

            IntList successors = block.getSuccessors();
            int succSize = successors.size();
            int primary = block.getPrimarySuccessor();

            if (((primary == -1) && (succSize != catchSize))
                    || ((primary != -1)
                            && ((succSize != (catchSize + 1))
                                    || (primary != successors.get(catchSize))))) {
                /*
                 * Blocks that throw are supposed to list their primary
                 * successor -- if any -- last in the successors list, but
                 * that constraint appears to be violated here.
                 */
                throw new RuntimeException(
                        "shouldn't happen: weird successors list");
            }

            /* Reduce the effective catchSize at the first catch-all. */
            for (int j = 0; j < catchSize; j++) {
                if (catches.getType(j).equals(Type.OBJECT)) {
                    catchSize = j + 1;
                    break;
                }
            }

            Type[] exceptionTypes = new Type[catchSize];
            CodeAddress[] handlerAddresses = new CodeAddress[catchSize];
            for (int j = 0; j < catchSize; j++) {
                exceptionTypes[j] = catches.getType(j);
                handlerAddresses[j] = addresses.getStart(successors.get(j));
            }

            result.add(new CatchBlock(addresses.getLast(block),
                    addresses.getEnd(block), exceptionTypes, handlerAddresses));
        }

        return result.toArray(new CatchBlock[result.size()]);
    }

    /** Makes a {@link CatchTable.Entry} for the given block range. */
    private static CatchTable.Entry makeEntry(CatchBlock start, CatchBlock end) {
        /* We start at the last instruction of the start block. */
        return new CatchTable.Entry(start.lastAddress.getAddress(),
                end.endAddress.getAddress(), start.toHandlerList());
    }

    /** Gets whether the address range is valid for a catch handler. */
    private static boolean rangeIsValid(CatchBlock start, CatchBlock end) {
        int startAddress = start.lastAddress.getAddress();
        int endAddress = end.endAddress.getAddress();

        return (endAddress - startAddress) <= MAX_CATCH_RANGE;
    }

    /** Compact snapshot of one catch-capable block. */
    private static final class CatchBlock {
        static final CatchBlock BARRIER =
                new CatchBlock(null, null, new Type[0], new CodeAddress[0]);

        final CodeAddress lastAddress;
        final CodeAddress endAddress;
        final Type[] exceptionTypes;
        final CodeAddress[] handlerAddresses;

        CatchBlock(CodeAddress lastAddress, CodeAddress endAddress,
                Type[] exceptionTypes, CodeAddress[] handlerAddresses) {
            this.lastAddress = lastAddress;
            this.endAddress = endAddress;
            this.exceptionTypes = exceptionTypes;
            this.handlerAddresses = handlerAddresses;
        }

        boolean hasHandlers() {
            return exceptionTypes.length != 0;
        }

        boolean sameHandlers(CatchBlock other) {
            if (exceptionTypes.length != other.exceptionTypes.length) {
                return false;
            }
            for (int i = 0; i < exceptionTypes.length; i++) {
                if (!exceptionTypes[i].equals(other.exceptionTypes[i])
                        || handlerAddresses[i] != other.handlerAddresses[i]) {
                    return false;
                }
            }
            return true;
        }

        CatchHandlerList toHandlerList() {
            CatchHandlerList result = new CatchHandlerList(exceptionTypes.length);
            for (int i = 0; i < exceptionTypes.length; i++) {
                result.set(i, new CstType(exceptionTypes[i]),
                        handlerAddresses[i].getAddress());
            }
            result.setImmutable();
            return result;
        }
    }
}
