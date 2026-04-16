/*******************************************************************************
 * Copyright (C) 2026, Leo Galambos
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.egothor.stemmer;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Encodes a compact patch command that transforms one word form into another
 * and applies such commands back to source words.
 *
 * <p>
 * The generated patch command follows the historical Egothor convention:
 * instructions are serialized so that they are applied from the end of the
 * source word toward its beginning. This keeps the command stream compact and
 * matches the behavior expected by existing stemming data.
 * </p>
 *
 * <p>
 * The encoder computes a minimum-cost edit script using weighted insert,
 * delete, replace, and match transitions. The resulting trace is then
 * serialized into the compact patch language.
 * </p>
 *
 * <p>
 * This class is stateful and reuses internal dynamic-programming matrices
 * across invocations to reduce allocation pressure during repeated use.
 * Instances are therefore not suitable for unsynchronized concurrent access.
 * The {@link #encode(String, String)} method is synchronized so that a shared
 * instance can still be used safely when needed.
 * </p>
 */
public final class PatchCommandEncoder {

    /**
     * Serialized opcode for deleting one or more characters.
     */
    private static final char DELETE_OPCODE = 'D';

    /**
     * Serialized opcode for inserting one character.
     */
    private static final char INSERT_OPCODE = 'I';

    /**
     * Serialized opcode for replacing one character.
     */
    private static final char REPLACE_OPCODE = 'R';

    /**
     * Serialized opcode for skipping one or more unchanged characters.
     */
    private static final char SKIP_OPCODE = '-';

    /**
     * Sentinel placed immediately before {@code 'a'} and used to accumulate compact
     * counts in the patch format.
     */
    private static final char COUNT_SENTINEL = (char) ('a' - 1);

    /**
     * Serialized opcode for a canonical no-operation patch.
     *
     * <p>
     * This opcode represents an identity transform of the whole source word. It is
     * used to ensure that equal source and target words always produce the same
     * serialized patch command.
     * </p>
     */
    private static final char NOOP_OPCODE = 'N';

    /**
     * Canonical argument used by the serialized no-operation patch.
     */
    private static final char NOOP_ARGUMENT = 'a';

    /**
     * Canonical serialized no-operation patch.
     *
     * <p>
     * This constant is returned by {@link #encode(String, String)} whenever source
     * and target are equal.
     * </p>
     */
    /* default */ static final String NOOP_PATCH = String.valueOf(new char[] { NOOP_OPCODE, NOOP_ARGUMENT });

    /**
     * Safety penalty used to prevent a mismatch from being selected as a match.
     */
    private static final int MISMATCH_PENALTY = 100;

    /**
     * Extra headroom added when internal matrices need to grow.
     */
    private static final int CAPACITY_MARGIN = 8;

    /**
     * Cost of inserting one character.
     */
    private final int insertCost;

    /**
     * Cost of deleting one character.
     */
    private final int deleteCost;

    /**
     * Cost of replacing one character.
     */
    private final int replaceCost;

    /**
     * Cost of keeping one matching character unchanged.
     */
    private final int matchCost;

    /**
     * Currently allocated source dimension of reusable matrices.
     */
    private int sourceCapacity;

    /**
     * Currently allocated target dimension of reusable matrices.
     */
    private int targetCapacity;

    /**
     * Dynamic-programming matrix containing cumulative minimum costs.
     */
    private int[][] costMatrix;

    /**
     * Matrix storing the chosen transition for each dynamic-programming cell.
     */
    private Trace[][] traceMatrix;

    /**
     * Reentrant lock for {@link #encode(String, String)} exclusive operation.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Internal dynamic-programming transition selected for one matrix cell.
     */
    private enum Trace {

        /**
         * Deletes one character from the source sequence.
         */
        DELETE,

        /**
         * Inserts one character from the target sequence.
         */
        INSERT,

        /**
         * Replaces one source character with one target character.
         */
        REPLACE,

        /**
         * Keeps one matching character unchanged.
         */
        MATCH
    }

    /**
     * Creates an encoder with the traditional Egothor cost model: insert = 1,
     * delete = 1, replace = 1, match = 0.
     */
    public PatchCommandEncoder() {
        this(1, 1, 1, 0);
    }

    /**
     * Creates an encoder with explicit operation costs.
     *
     * @param insertCost  cost of inserting one character
     * @param deleteCost  cost of deleting one character
     * @param replaceCost cost of replacing one character
     * @param matchCost   cost of keeping one equal character unchanged
     */
    public PatchCommandEncoder(int insertCost, int deleteCost, int replaceCost, int matchCost) {
        if (insertCost < 0) {
            throw new IllegalArgumentException("insertCost must be non-negative.");
        }
        if (deleteCost < 0) {
            throw new IllegalArgumentException("deleteCost must be non-negative.");
        }
        if (replaceCost < 0) {
            throw new IllegalArgumentException("replaceCost must be non-negative.");
        }
        if (matchCost < 0) {
            throw new IllegalArgumentException("matchCost must be non-negative.");
        }

        this.insertCost = insertCost;
        this.deleteCost = deleteCost;
        this.replaceCost = replaceCost;
        this.matchCost = matchCost;
        this.sourceCapacity = 0;
        this.targetCapacity = 0;
        this.costMatrix = new int[0][0];
        this.traceMatrix = new Trace[0][0];
    }

    /**
     * Produces a compact patch command that transforms {@code source} into
     * {@code target}.
     *
     * @param source source word form
     * @param target target word form
     * @return compact patch command, or {@code null} when any argument is
     *         {@code null}
     */
    public String encode(String source, String target) {
        if (source == null || target == null) {
            return null;
        }

        if (source.equals(target)) {
            return NOOP_PATCH;
        }

        int sourceLength = source.length();
        int targetLength = target.length();

        lock.lock();
        try {
            ensureCapacity(sourceLength + 1, targetLength + 1);
            initializeBoundaryConditions(sourceLength, targetLength);

            char[] sourceCharacters = source.toCharArray();
            char[] targetCharacters = target.toCharArray();

            fillMatrices(sourceCharacters, targetCharacters, sourceLength, targetLength);

            return buildPatchCommand(targetCharacters, sourceLength, targetLength);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Applies a compact patch command to the supplied source word.
     *
     * <p>
     * This method operates directly on serialized opcodes rather than mapping them
     * to another representation. That keeps the hot path small and avoids
     * unnecessary indirection during patch application.
     * </p>
     *
     * <p>
     * For compatibility with the historical behavior, malformed patch input that
     * causes index failures results in the original source word being returned
     * unchanged.
     * </p>
     *
     * @param source       original source word
     * @param patchCommand compact patch command
     * @return transformed word, or {@code null} when {@code source} is {@code null}
     */
    public static String apply(String source, String patchCommand) {
        if (source == null) {
            return null;
        }
        if (patchCommand == null || patchCommand.isEmpty()) {
            return source;
        }
        if (NOOP_PATCH.equals(patchCommand)) {
            return source;
        }

        StringBuilder result = new StringBuilder(source);

        if (result.isEmpty()) {
            return applyToEmptySource(result, patchCommand);
        }

        int position = result.length() - 1;

        try {
            for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) { // NOPMD

                char opcode = patchCommand.charAt(patchIndex);
                char argument = patchCommand.charAt(patchIndex + 1);
                int encodedCount = argument - 'a' + 1;

                switch (opcode) {
                    case SKIP_OPCODE:
                        position = position - encodedCount + 1;
                        break;

                    case REPLACE_OPCODE:
                        result.setCharAt(position, argument);
                        break;

                    case DELETE_OPCODE:
                        int deleteEndExclusive = position + 1;
                        position -= encodedCount - 1;
                        result.delete(position, deleteEndExclusive);
                        break;

                    case INSERT_OPCODE:
                        result.insert(position + 1, argument);
                        position++;
                        break;

                    case NOOP_OPCODE:
                        if (argument != NOOP_ARGUMENT) {
                            throw new IllegalArgumentException("Unsupported NOOP patch argument: " + argument);
                        }
                        return source;

                    default:
                        throw new IllegalArgumentException("Unsupported patch opcode: " + opcode);
                }

                position--;
            }
        } catch (IndexOutOfBoundsException exception) {
            return source;
        }

        return result.toString();
    }

    /**
     * Applies a patch command to an empty source word.
     *
     * <p>
     * Only insertion instructions are meaningful for an empty source. Skip,
     * replace, and delete instructions are treated as malformed and therefore cause
     * the original source to be preserved, consistent with the historical fallback
     * behavior for index-invalid commands.
     * </p>
     *
     * @param result       empty result builder
     * @param patchCommand compact patch command
     * @return transformed word, or the original empty word when the patch is
     *         malformed
     */
    private static String applyToEmptySource(StringBuilder result, String patchCommand) {
        try {
            for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) { // NOPMD

                char opcode = patchCommand.charAt(patchIndex);
                char argument = patchCommand.charAt(patchIndex + 1);

                switch (opcode) {
                    case INSERT_OPCODE:
                        result.insert(0, argument);
                        break;

                    case SKIP_OPCODE:
                    case REPLACE_OPCODE:
                    case DELETE_OPCODE:
                        return "";

                    case NOOP_OPCODE:
                        if (argument != NOOP_ARGUMENT) {
                            throw new IllegalArgumentException("Unsupported NOOP patch argument: " + argument);
                        }
                        return "";

                    default:
                        throw new IllegalArgumentException("Unsupported patch opcode: " + opcode);
                }
            }
        } catch (IndexOutOfBoundsException exception) {
            return "";
        }

        return result.toString();
    }

    /**
     * Ensures that internal matrices are large enough for the requested input
     * dimensions.
     *
     * @param requiredSourceCapacity required source dimension
     * @param requiredTargetCapacity required target dimension
     */
    private void ensureCapacity(int requiredSourceCapacity, int requiredTargetCapacity) {
        if (requiredSourceCapacity <= sourceCapacity && requiredTargetCapacity <= targetCapacity) {
            return;
        }

        sourceCapacity = Math.max(sourceCapacity, requiredSourceCapacity) + CAPACITY_MARGIN;
        targetCapacity = Math.max(targetCapacity, requiredTargetCapacity) + CAPACITY_MARGIN;

        costMatrix = new int[sourceCapacity][targetCapacity];
        traceMatrix = new Trace[sourceCapacity][targetCapacity];
    }

    /**
     * Initializes the first row and first column of the dynamic-programming
     * matrices.
     *
     * @param sourceLength length of the source word
     * @param targetLength length of the target word
     */
    private void initializeBoundaryConditions(int sourceLength, int targetLength) {
        costMatrix[0][0] = 0;
        traceMatrix[0][0] = Trace.MATCH;

        for (int sourceIndex = 1; sourceIndex <= sourceLength; sourceIndex++) {
            costMatrix[sourceIndex][0] = sourceIndex * deleteCost;
            traceMatrix[sourceIndex][0] = Trace.DELETE;
        }

        for (int targetIndex = 1; targetIndex <= targetLength; targetIndex++) {
            costMatrix[0][targetIndex] = targetIndex * insertCost;
            traceMatrix[0][targetIndex] = Trace.INSERT;
        }
    }

    /**
     * Fills dynamic-programming matrices for the supplied source and target
     * character sequences.
     *
     * @param sourceCharacters source characters
     * @param targetCharacters target characters
     * @param sourceLength     source length
     * @param targetLength     target length
     */
    private void fillMatrices(char[] sourceCharacters, char[] targetCharacters, int sourceLength, int targetLength) {

        for (int sourceIndex = 1; sourceIndex <= sourceLength; sourceIndex++) {
            char sourceCharacter = sourceCharacters[sourceIndex - 1];

            for (int targetIndex = 1; targetIndex <= targetLength; targetIndex++) {
                char targetCharacter = targetCharacters[targetIndex - 1];

                int deleteCandidate = costMatrix[sourceIndex - 1][targetIndex] + deleteCost;
                int insertCandidate = costMatrix[sourceIndex][targetIndex - 1] + insertCost;
                int replaceCandidate = costMatrix[sourceIndex - 1][targetIndex - 1] + replaceCost;
                int matchCandidate = costMatrix[sourceIndex - 1][targetIndex - 1]
                        + (sourceCharacter == targetCharacter ? matchCost : MISMATCH_PENALTY);

                int bestCost = matchCandidate;
                Trace bestTrace = Trace.MATCH;

                if (deleteCandidate <= bestCost) {
                    bestCost = deleteCandidate;
                    bestTrace = Trace.DELETE;
                }
                if (insertCandidate < bestCost) {
                    bestCost = insertCandidate;
                    bestTrace = Trace.INSERT;
                }
                if (replaceCandidate < bestCost) {
                    bestCost = replaceCandidate;
                    bestTrace = Trace.REPLACE;
                }

                costMatrix[sourceIndex][targetIndex] = bestCost;
                traceMatrix[sourceIndex][targetIndex] = bestTrace;
            }
        }
    }

    /**
     * Reconstructs the compact patch command by traversing the trace matrix from
     * the final cell back to the origin.
     *
     * @param targetCharacters target characters
     * @param sourceLength     source length
     * @param targetLength     target length
     * @return compact patch command
     */
    private String buildPatchCommand(char[] targetCharacters, int sourceLength, int targetLength) {

        StringBuilder patchBuilder = new StringBuilder(sourceLength + targetLength);

        char pendingDeletes = COUNT_SENTINEL;
        char pendingSkips = COUNT_SENTINEL;

        int sourceIndex = sourceLength;
        int targetIndex = targetLength;

        while (sourceIndex != 0 || targetIndex != 0) {
            Trace trace = traceMatrix[sourceIndex][targetIndex];

            switch (trace) {
                case DELETE:
                    if (pendingSkips != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, SKIP_OPCODE, pendingSkips);
                        pendingSkips = COUNT_SENTINEL;
                    }
                    pendingDeletes++;
                    sourceIndex--;
                    break;

                case INSERT:
                    if (pendingDeletes != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, DELETE_OPCODE, pendingDeletes);
                        pendingDeletes = COUNT_SENTINEL;
                    }
                    if (pendingSkips != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, SKIP_OPCODE, pendingSkips);
                        pendingSkips = COUNT_SENTINEL;
                    }
                    targetIndex--;
                    appendInstruction(patchBuilder, INSERT_OPCODE, targetCharacters[targetIndex]);
                    break;

                case REPLACE:
                    if (pendingDeletes != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, DELETE_OPCODE, pendingDeletes);
                        pendingDeletes = COUNT_SENTINEL;
                    }
                    if (pendingSkips != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, SKIP_OPCODE, pendingSkips);
                        pendingSkips = COUNT_SENTINEL;
                    }
                    targetIndex--;
                    sourceIndex--;
                    appendInstruction(patchBuilder, REPLACE_OPCODE, targetCharacters[targetIndex]);
                    break;

                case MATCH:
                    if (pendingDeletes != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, DELETE_OPCODE, pendingDeletes);
                        pendingDeletes = COUNT_SENTINEL;
                    }
                    pendingSkips++;
                    sourceIndex--;
                    targetIndex--;
                    break;
            }
        }

        if (pendingDeletes != COUNT_SENTINEL) {
            appendInstruction(patchBuilder, DELETE_OPCODE, pendingDeletes);
        }

        return patchBuilder.toString();
    }

    /**
     * Appends one serialized instruction to the patch command builder.
     *
     * @param patchBuilder patch command builder
     * @param opcode       single-character instruction opcode
     * @param argument     encoded instruction argument
     */
    private static void appendInstruction(StringBuilder patchBuilder, char opcode, char argument) {
        patchBuilder.append(opcode).append(argument);
    }
}
