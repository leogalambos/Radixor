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

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Encodes a compact patch command that transforms one word form into another
 * and applies such commands back to source words.
 *
 * <p>
 * The historical Egothor patch language is defined for backward traversal, that
 * is, from the logical end of a word toward its beginning. This implementation
 * preserves that proven opcode semantics as the single internal representation.
 * Forward traversal is implemented by translating source and target words to
 * the equivalent reversed logical form at the API boundary and then delegating
 * to the same backward encoder and decoder.
 * </p>
 *
 * <p>
 * This design keeps the patch language stable, avoids maintaining two distinct
 * opcode interpreters, and guarantees that forward traversal is semantically
 * equivalent to running the historical algorithm on the reversed logical word
 * form.
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
@SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition", "PMD.CyclomaticComplexity", "PMD.ForLoopVariableCount" })
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
     */
    private static final char NOOP_OPCODE = 'N';

    /**
     * Canonical argument used by the serialized no-operation patch.
     */
    private static final char NOOP_ARGUMENT = 'a';

    /**
     * Canonical serialized no-operation patch.
     */
    /* default */ static final String NOOP_PATCH = String.valueOf(new char[] { NOOP_OPCODE, NOOP_ARGUMENT });

    /**
     * Return value used by
     * {@link #applyTo(CharSequence, String, WordTraversalDirection, char[], int, int)}
     * when the caller-owned output range is too small for the transformed text.
     */
    public static final int APPLY_INSUFFICIENT_CAPACITY = -1;

    /**
     * Prefix used in unsupported NOOP patch argument exceptions.
     */
    private static final String MSG_NOOP = "Unsupported NOOP patch argument: ";

    /**
     * Prefix used in unsupported patch opcode exceptions.
     */
    private static final String MSG_OPCODE = "Unsupported patch opcode: ";

    /**
     * Safety penalty used to prevent a mismatch from being selected as a match.
     */
    private static final int MISMATCH_PENALTY = 100;

    /**
     * Extra matrix headroom reserved beyond the immediately required dimensions.
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
     * Direction in which words are traversed during both patch serialization and
     * patch application.
     */
    private final WordTraversalDirection traversalDirection;

    /**
     * Whether this instance applies patch commands in backward traversal order.
     */
    private final boolean backwardTraversal;

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

        /** Deletes one character from the source sequence. */
        DELETE,

        /** Inserts one character from the target sequence. */
        INSERT,

        /** Replaces one source character with one target character. */
        REPLACE,

        /** Keeps one matching character unchanged. */
        MATCH
    }

    private PatchCommandEncoder(final Builder builder) {
        this.traversalDirection = Objects.requireNonNull(builder.traversalDirection, "traversalDirection");
        final int insertCost = builder.insertCost;
        if (insertCost < 0) {
            throw new IllegalArgumentException("insertCost must be non-negative.");
        }
        final int deleteCost = builder.deleteCost;
        if (deleteCost < 0) {
            throw new IllegalArgumentException("deleteCost must be non-negative.");
        }
        final int replaceCost = builder.replaceCost;
        if (replaceCost < 0) {
            throw new IllegalArgumentException("replaceCost must be non-negative.");
        }
        final int matchCost = builder.matchCost;
        if (matchCost < 0) {
            throw new IllegalArgumentException("matchCost must be non-negative.");
        }

        this.insertCost = insertCost;
        this.deleteCost = deleteCost;
        this.replaceCost = replaceCost;
        this.matchCost = matchCost;
        this.backwardTraversal = this.traversalDirection == WordTraversalDirection.BACKWARD;
        this.sourceCapacity = 0;
        this.targetCapacity = 0;
        this.costMatrix = new int[0][0];
        this.traceMatrix = new Trace[0][0];
    }

    /**
     * Creates a fluent builder for constructing a direction-specialized encoder.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
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
    public String encode(final String source, final String target) {
        if (source == null || target == null) {
            return null;
        }
        if (source.equals(target)) {
            return NOOP_PATCH;
        }

        if (this.traversalDirection == WordTraversalDirection.BACKWARD) {
            return encodeBackward(source, target);
        }
        return encodeForward(source, target);
    }

    /**
     * Applies a compact patch command using this encoder instance traversal
     * direction.
     *
     * <p>
     * This is the instance-level fast path for repeated patch application in a
     * known traversal direction. It avoids the static API null and direction
     * validation path and calls the selected decoder directly.
     * </p>
     *
     * @param source       original source word
     * @param patchCommand compact patch command
     * @return transformed word, or {@code null} when {@code source} is {@code null}
     * @deprecated Since 2.3.0. Runtime stemming should compile
     *             {@code patchCommand} once through {@link #compile(String)} and
     *             reuse {@link CompiledPatchCommand#apply(String)}. The
     *             String-based application path reparses the patch command on every
     *             call and is kept only for source compatibility before the 3.0.0
     *             migration.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public String applyWithConfiguredDirection(final String source, final String patchCommand) {
        if (source == null) {
            return null;
        }
        if (this.backwardTraversal) {
            return applyBackwardNonNull(source, patchCommand);
        }
        return applyForwardNonNull(source, patchCommand);
    }

    /**
     * Compiles a patch command for repeated application with this encoder
     * instance traversal direction.
     *
     * @param patchCommand compact patch command
     * @return immutable compiled patch command
     * @throws IllegalArgumentException if the serialized command contains an
     *                                  unsupported opcode or invalid NOOP argument
     */
    public CompiledPatchCommand compile(final String patchCommand) {
        return CompiledPatchCommand.compile(patchCommand, this.traversalDirection);
    }

    /**
     * Applies a compact patch command to the supplied source word using the
     * historical backward traversal direction.
     *
     * @param source       original source word
     * @param patchCommand compact patch command
     * @return transformed word, or {@code null} when {@code source} is {@code null}
     * @deprecated Since 2.3.0. Runtime stemming should use
     *             {@link CompiledPatchCommand#compile(String, WordTraversalDirection)}
     *             once and then reuse {@link CompiledPatchCommand#apply(String)}.
     *             This method repeatedly interprets the serialized patch-command
     *             string and is retained only for compatibility before the 3.0.0
     *             migration.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static String apply(final String source, final String patchCommand) {
        return apply(source, patchCommand, WordTraversalDirection.BACKWARD);
    }

    /**
     * Applies a compact patch command to the supplied source word using the
     * specified traversal direction.
     *
     * <p>
     * The implementation uses dedicated direction-specific patch decoders.
     * </p>
     *
     * @param source             original source word
     * @param patchCommand       compact patch command
     * @param traversalDirection traversal direction used by the patch command
     * @return transformed word, or {@code null} when {@code source} is {@code null}
     * @deprecated Since 2.3.0. Runtime stemming should use
     *             {@link CompiledPatchCommand#compile(String, WordTraversalDirection)}
     *             once and then reuse {@link CompiledPatchCommand#apply(String)}.
     *             This method repeatedly interprets the serialized patch-command
     *             string and is retained only for compatibility before the 3.0.0
     *             migration.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static String apply(final String source, final String patchCommand,
            final WordTraversalDirection traversalDirection) {
        Objects.requireNonNull(traversalDirection, "traversalDirection");
        if (source == null) {
            return null;
        }
        if (traversalDirection == WordTraversalDirection.BACKWARD) {
            return applyBackwardNonNull(source, patchCommand);
        }
        return applyForwardNonNull(source, patchCommand);
    }

    /**
     * Compiles a patch command for repeated application with the supplied
     * traversal direction.
     *
     * @param patchCommand       compact patch command
     * @param traversalDirection traversal direction used by the patch command
     * @return immutable compiled patch command
     * @throws NullPointerException     if {@code traversalDirection} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the serialized command contains an
     *                                  unsupported opcode or invalid NOOP argument
     */
    public static CompiledPatchCommand compile(final String patchCommand,
            final WordTraversalDirection traversalDirection) {
        return CompiledPatchCommand.compile(patchCommand, traversalDirection);
    }

    /**
     * Applies a compact patch command into a caller-owned output buffer.
     *
     * <p>
     * The output array is not retained. Capacity failure is reported by
     * {@link #APPLY_INSUFFICIENT_CAPACITY} and leaves the output range unchanged.
     * Malformed compatibility cases preserve the source exactly as
     * {@link #apply(String, String, WordTraversalDirection)} does.
     * </p>
     *
     * @param source             original source text
     * @param patchCommand       compact patch command
     * @param traversalDirection traversal direction used by the patch command
     * @param output             caller-owned output storage
     * @param outputOffset       first writable output offset
     * @param outputLength       writable output capacity
     * @return produced character count, or {@link #APPLY_INSUFFICIENT_CAPACITY}
     *         when {@code outputLength} is too small
     * @deprecated Since 2.3.0. Compile {@code patchCommand} once through
     *             {@link #compile(String, WordTraversalDirection)} and call
     *             {@link CompiledPatchCommand#applyTo(CharSequence, char[], int, int)}
     *             or
     *             {@link CompiledPatchCommand#applyTo(CharSequence, int, int, char[], int, int)}.
     *             This String-based method reparses patch commands on every call and
     *             is kept only for compatibility before the 3.0.0 migration.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static int applyTo(final CharSequence source, final String patchCommand,
            final WordTraversalDirection traversalDirection, final char[] output, final int outputOffset,
            final int outputLength) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(traversalDirection, "traversalDirection");
        Objects.requireNonNull(output, "output");
        Objects.checkFromIndexSize(outputOffset, outputLength, output.length);

        final int sourceLength = source.length();
        final int producedLength = computeAppliedLength(sourceLength, patchCommand, traversalDirection);
        if (producedLength > outputLength) {
            return APPLY_INSUFFICIENT_CAPACITY;
        }
        applyToOutput(source, 0, sourceLength, patchCommand, traversalDirection, output, outputOffset, producedLength);
        return producedLength;
    }

    /**
     * Applies a compact patch command from a caller-owned source slice into a
     * caller-owned output buffer.
     *
     * @param source             source storage
     * @param sourceOffset       first source character offset
     * @param sourceLength       number of source characters
     * @param patchCommand       compact patch command
     * @param traversalDirection traversal direction used by the patch command
     * @param output             caller-owned output storage
     * @param outputOffset       first writable output offset
     * @param outputLength       writable output capacity
     * @return produced character count, or {@link #APPLY_INSUFFICIENT_CAPACITY}
     *         when {@code outputLength} is too small
     * @throws IllegalArgumentException when source and output ranges overlap in the
     *                                  same array
     * @deprecated Since 2.3.0. Compile {@code patchCommand} once through
     *             {@link #compile(String, WordTraversalDirection)} and call
     *             {@link CompiledPatchCommand#applyTo(char[], int, int, char[], int, int)}.
     *             This String-based method reparses patch commands on every call and
     *             is kept only for compatibility before the 3.0.0 migration.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static int applyTo(final char[] source, final int sourceOffset, final int sourceLength,
            final String patchCommand, final WordTraversalDirection traversalDirection, final char[] output,
            final int outputOffset, final int outputLength) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(traversalDirection, "traversalDirection");
        Objects.requireNonNull(output, "output");
        Objects.checkFromIndexSize(sourceOffset, sourceLength, source.length);
        Objects.checkFromIndexSize(outputOffset, outputLength, output.length);
        validateNonOverlappingRanges(source, sourceOffset, sourceLength, output, outputOffset, outputLength);

        final int producedLength = computeAppliedLength(sourceLength, patchCommand, traversalDirection);
        if (producedLength > outputLength) {
            return APPLY_INSUFFICIENT_CAPACITY;
        }
        applyToOutput(source, sourceOffset, sourceLength, patchCommand, traversalDirection, output, outputOffset,
                producedLength);
        return producedLength;
    }

    /**
     * Encodes a patch command using the historical backward Egothor semantics.
     *
     * @param source source word form in legacy backward logical space
     * @param target target word form in legacy backward logical space
     * @return compact patch command
     */
    private String encodeBackward(final String source, final String target) {
        final int sourceLength = source.length();
        final int targetLength = target.length();

        lock.lock();
        try {
            ensureCapacity(sourceLength + 1, targetLength + 1);
            initializeBoundaryConditionsBackward(sourceLength, targetLength);

            final char[] sourceCharacters = source.toCharArray();
            final char[] targetCharacters = target.toCharArray();

            fillMatrices(sourceCharacters, targetCharacters, sourceLength, targetLength,
                    WordTraversalDirection.BACKWARD);

            return buildPatchCommandBackward(targetCharacters, sourceLength, targetLength);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Encodes a patch command using forward traversal semantics.
     *
     * @param source source word form
     * @param target target word form
     * @return compact patch command
     */
    private String encodeForward(final String source, final String target) {
        final int sourceLength = source.length();
        final int targetLength = target.length();

        lock.lock();
        try {
            ensureCapacity(sourceLength + 1, targetLength + 1);
            initializeBoundaryConditionsForward(sourceLength, targetLength);

            final char[] sourceCharacters = source.toCharArray();
            final char[] targetCharacters = target.toCharArray();

            fillMatrices(sourceCharacters, targetCharacters, sourceLength, targetLength,
                    WordTraversalDirection.FORWARD);

            return buildPatchCommandForward(targetCharacters, sourceLength, targetLength);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Applies a patch command using the historical backward Egothor semantics.
     *
     * @param source       non-null original source word in legacy backward logical
     *                     space
     * @param patchCommand compact patch command
     * @return transformed word
     */
    private static String applyBackwardNonNull(final String source, final String patchCommand) {
        if (patchCommand == null) {
            return source;
        }
        final int patchLength = patchCommand.length();
        if (patchLength == 0 || (patchLength & 1) != 0) {
            return source;
        }
        if (patchLength == 2) {
            return applySingleBackwardInstruction(source, patchCommand.charAt(0), patchCommand.charAt(1));
        }

        if (source.isEmpty()) {
            return applyBackwardToEmptySource(patchCommand);
        }

        final StringBuilder result = new StringBuilder(source);

        int position = result.length() - 1;

        try {
            for (int patchIndex = 0; patchIndex < patchLength; patchIndex += 2) {
                final char opcode = patchCommand.charAt(patchIndex);
                final char argument = patchCommand.charAt(patchIndex + 1);

                switch (opcode) {
                    case SKIP_OPCODE:
                        final int skipCount = decodeEncodedCount(argument);
                        if (skipCount < 1) {
                            return source;
                        }
                        position = position - skipCount + 1;
                        break;

                    case REPLACE_OPCODE:
                        result.setCharAt(position, argument);
                        break;

                    case DELETE_OPCODE:
                        final int deleteCount = decodeEncodedCount(argument);
                        if (deleteCount < 1) {
                            return source;
                        }
                        final int deleteEndExclusive = position + 1;
                        position -= deleteCount - 1;
                        result.delete(position, deleteEndExclusive);
                        break;

                    case INSERT_OPCODE:
                        result.insert(position + 1, argument);
                        position++;
                        break;

                    case NOOP_OPCODE:
                        if (argument != NOOP_ARGUMENT) {
                            throw new IllegalArgumentException(MSG_NOOP + argument);
                        }
                        return source;

                    default:
                        throw new IllegalArgumentException(MSG_OPCODE + opcode);
                }

                position--;
            }
        } catch (IndexOutOfBoundsException exception) {
            return source;
        }

        return result.toString();
    }

    /**
     * Applies a patch command using forward traversal semantics.
     *
     * @param source       non-null original source word
     * @param patchCommand compact patch command
     * @return transformed word
     */
    private static String applyForwardNonNull(final String source, final String patchCommand) {
        if (patchCommand == null) {
            return source;
        }
        final int patchLength = patchCommand.length();
        if (patchLength == 0 || (patchLength & 1) != 0) {
            return source;
        }
        if (patchLength == 2) {
            return applySingleForwardInstruction(source, patchCommand.charAt(0), patchCommand.charAt(1));
        }

        if (source.isEmpty()) {
            return applyForwardToEmptySource(patchCommand);
        }

        final StringBuilder result = new StringBuilder(source);

        int position = 0;

        try {
            for (int patchIndex = 0; patchIndex < patchLength; patchIndex += 2) {
                final char opcode = patchCommand.charAt(patchIndex);
                final char argument = patchCommand.charAt(patchIndex + 1);

                switch (opcode) {
                    case SKIP_OPCODE:
                        final int skipCount = decodeEncodedCount(argument);
                        if (skipCount < 1) {
                            return source;
                        }
                        position = position + skipCount - 1;
                        break;

                    case REPLACE_OPCODE:
                        result.setCharAt(position, argument);
                        break;

                    case DELETE_OPCODE:
                        final int deleteCount = decodeEncodedCount(argument);
                        if (deleteCount < 1) {
                            return source;
                        }
                        result.delete(position, position + deleteCount);
                        position--;
                        break;

                    case INSERT_OPCODE:
                        result.insert(position, argument);
                        break;

                    case NOOP_OPCODE:
                        if (argument != NOOP_ARGUMENT) {
                            throw new IllegalArgumentException(MSG_NOOP + argument);
                        }
                        return source;

                    default:
                        throw new IllegalArgumentException(MSG_OPCODE + opcode);
                }

                position++;
            }
        } catch (IndexOutOfBoundsException exception) {
            return source;
        }

        return result.toString();
    }

    /**
     * Applies a single backward-direction patch instruction.
     *
     * @param source   original source word
     * @param opcode   patch opcode
     * @param argument encoded patch argument
     * @return transformed source after one instruction
     */
    private static String applySingleBackwardInstruction(final String source, final char opcode, final char argument) {
        final int sourceLength = source.length();
        final int encodedValue;

        switch (opcode) {
            case DELETE_OPCODE:
                encodedValue = decodeEncodedCount(argument);
                if (encodedValue < 1 || encodedValue > sourceLength) {
                    return source;
                }
                return source.substring(0, sourceLength - encodedValue);

            case INSERT_OPCODE:
                final char[] insertTarget = new char[sourceLength + 1];
                source.getChars(0, sourceLength, insertTarget, 0);
                insertTarget[sourceLength] = argument;
                return new String(insertTarget);

            case REPLACE_OPCODE:
                if (sourceLength == 0) {
                    return source;
                }
                final char[] replaceTarget = source.toCharArray();
                replaceTarget[sourceLength - 1] = argument;
                return new String(replaceTarget);

            case SKIP_OPCODE:
                return source;

            case NOOP_OPCODE:
                if (argument != NOOP_ARGUMENT) {
                    throw new IllegalArgumentException(MSG_NOOP + argument);
                }
                return source;

            default:
                throw new IllegalArgumentException(MSG_OPCODE + opcode);
        }
    }

    /**
     * Applies a single forward-direction patch instruction.
     *
     * @param source   original source word
     * @param opcode   patch opcode
     * @param argument encoded patch argument
     * @return transformed source after one instruction
     */
    private static String applySingleForwardInstruction(final String source, final char opcode, final char argument) {
        final int sourceLength = source.length();
        final int encodedValue;

        switch (opcode) {
            case DELETE_OPCODE:
                encodedValue = decodeEncodedCount(argument);
                if (encodedValue < 1 || encodedValue > sourceLength) {
                    return source;
                }
                return source.substring(encodedValue);

            case INSERT_OPCODE:
                final char[] insertTarget = new char[sourceLength + 1];
                insertTarget[0] = argument;
                source.getChars(0, sourceLength, insertTarget, 1);
                return new String(insertTarget);

            case REPLACE_OPCODE:
                if (sourceLength == 0) {
                    return source;
                }
                final char[] replaceTarget = source.toCharArray();
                replaceTarget[0] = argument;
                return new String(replaceTarget);

            case SKIP_OPCODE:
                return source;

            case NOOP_OPCODE:
                if (argument != NOOP_ARGUMENT) {
                    throw new IllegalArgumentException(MSG_NOOP + argument);
                }
                return source;

            default:
                throw new IllegalArgumentException(MSG_OPCODE + opcode);
        }
    }

    /**
     * Applies a backward patch command to an empty source word.
     *
     * <p>
     * Only insertion instructions are meaningful for an empty source. Skip,
     * replace, and delete instructions are treated as malformed and therefore cause
     * the original source to be preserved, consistent with the historical fallback
     * behavior for index-invalid commands.
     * </p>
     *
     * @param patchCommand compact patch command
     * @return transformed word, or the original empty word when the patch is
     *         malformed
     */
    private static String applyBackwardToEmptySource(final String patchCommand) {
        final StringBuilder result = new StringBuilder(patchCommand.length() >> 1);
        try {
            for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
                final char opcode = patchCommand.charAt(patchIndex);
                final char argument = patchCommand.charAt(patchIndex + 1);

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
                            throw new IllegalArgumentException(MSG_NOOP + argument);
                        }
                        return "";

                    default:
                        throw new IllegalArgumentException(MSG_OPCODE + opcode);
                }
            }
        } catch (IndexOutOfBoundsException exception) {
            return "";
        }

        return result.toString();
    }

    /**
     * Applies a forward patch command to an empty source word.
     *
     * @param patchCommand compact patch command
     * @return transformed word, or the original empty word when the patch is
     *         malformed
     */
    private static String applyForwardToEmptySource(final String patchCommand) {
        final StringBuilder result = new StringBuilder(patchCommand.length() >> 1);
        try {
            for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
                final char opcode = patchCommand.charAt(patchIndex);
                final char argument = patchCommand.charAt(patchIndex + 1);

                switch (opcode) {
                    case INSERT_OPCODE:
                        result.append(argument);
                        break;

                    case SKIP_OPCODE:
                    case REPLACE_OPCODE:
                    case DELETE_OPCODE:
                        return "";

                    case NOOP_OPCODE:
                        if (argument != NOOP_ARGUMENT) {
                            throw new IllegalArgumentException(MSG_NOOP + argument);
                        }
                        return "";

                    default:
                        throw new IllegalArgumentException(MSG_OPCODE + opcode);
                }
            }
        } catch (IndexOutOfBoundsException exception) {
            return "";
        }

        return result.toString();
    }

    /**
     * Computes the transformed length or the preserved source length for malformed
     * compatibility cases.
     *
     * @param sourceLength       source length
     * @param patchCommand       patch command
     * @param traversalDirection traversal direction
     * @return produced length
     */
    private static int computeAppliedLength(final int sourceLength, final String patchCommand,
            final WordTraversalDirection traversalDirection) {
        if (patchCommand == null || patchCommand.isEmpty() || NOOP_PATCH.equals(patchCommand)
                || (patchCommand.length() & 1) != 0) {
            return sourceLength;
        }
        if (traversalDirection == WordTraversalDirection.BACKWARD) {
            return computeBackwardAppliedLength(sourceLength, patchCommand);
        }
        return computeForwardAppliedLength(sourceLength, patchCommand);
    }

    /**
     * Computes the backward traversal output length.
     *
     * @param sourceLength source length
     * @param patchCommand patch command
     * @return produced length
     */
    private static int computeBackwardAppliedLength(final int sourceLength, final String patchCommand) {
        if (patchCommand.length() == 2) {
            return computeSingleBackwardAppliedLength(sourceLength, patchCommand.charAt(0), patchCommand.charAt(1));
        }
        if (sourceLength == 0) {
            return computeBackwardEmptyAppliedLength(patchCommand);
        }

        int currentLength = sourceLength;
        int position = sourceLength - 1;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);

            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = decodeEncodedCount(argument);
                    if (skipCount < 1) {
                        return sourceLength;
                    }
                    position = position - skipCount + 1;
                    break;

                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength) {
                        return sourceLength;
                    }
                    break;

                case DELETE_OPCODE:
                    final int deleteCount = decodeEncodedCount(argument);
                    if (deleteCount < 1) {
                        return sourceLength;
                    }
                    final int deleteEndExclusive = position + 1;
                    position -= deleteCount - 1;
                    if (position < 0 || deleteEndExclusive > currentLength || position > deleteEndExclusive) {
                        return sourceLength;
                    }
                    currentLength -= deleteEndExclusive - position;
                    break;

                case INSERT_OPCODE:
                    if (position < -1 || position >= currentLength) {
                        return sourceLength;
                    }
                    currentLength++;
                    position++;
                    break;

                case NOOP_OPCODE:
                    if (argument != NOOP_ARGUMENT) {
                        throw new IllegalArgumentException(MSG_NOOP + argument);
                    }
                    return sourceLength;

                default:
                    throw new IllegalArgumentException(MSG_OPCODE + opcode);
            }

            position--;
        }
        return currentLength;
    }

    /**
     * Computes the forward traversal output length.
     *
     * @param sourceLength source length
     * @param patchCommand patch command
     * @return produced length
     */
    private static int computeForwardAppliedLength(final int sourceLength, final String patchCommand) {
        if (patchCommand.length() == 2) {
            return computeSingleForwardAppliedLength(sourceLength, patchCommand.charAt(0), patchCommand.charAt(1));
        }
        if (sourceLength == 0) {
            return computeForwardEmptyAppliedLength(patchCommand);
        }

        int currentLength = sourceLength;
        int position = 0;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);

            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = decodeEncodedCount(argument);
                    if (skipCount < 1) {
                        return sourceLength;
                    }
                    position = position + skipCount - 1;
                    break;

                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength) {
                        return sourceLength;
                    }
                    break;

                case DELETE_OPCODE:
                    final int deleteCount = decodeEncodedCount(argument);
                    if (deleteCount < 1 || position < 0 || position + deleteCount > currentLength) {
                        return sourceLength;
                    }
                    currentLength -= deleteCount;
                    position--;
                    break;

                case INSERT_OPCODE:
                    if (position < 0 || position > currentLength) {
                        return sourceLength;
                    }
                    currentLength++;
                    break;

                case NOOP_OPCODE:
                    if (argument != NOOP_ARGUMENT) {
                        throw new IllegalArgumentException(MSG_NOOP + argument);
                    }
                    return sourceLength;

                default:
                    throw new IllegalArgumentException(MSG_OPCODE + opcode);
            }

            position++;
        }
        return currentLength;
    }

    /**
     * Computes a single backward instruction output length.
     *
     * @param sourceLength source length
     * @param opcode       opcode
     * @param argument     argument
     * @return produced length
     */
    private static int computeSingleBackwardAppliedLength(final int sourceLength, final char opcode,
            final char argument) {
        final int encodedValue;
        switch (opcode) {
            case DELETE_OPCODE:
                encodedValue = decodeEncodedCount(argument);
                return encodedValue < 1 || encodedValue > sourceLength ? sourceLength : sourceLength - encodedValue;
            case INSERT_OPCODE:
                return sourceLength + 1;
            case REPLACE_OPCODE:
            case SKIP_OPCODE:
                return sourceLength;
            case NOOP_OPCODE:
                if (argument != NOOP_ARGUMENT) {
                    throw new IllegalArgumentException(MSG_NOOP + argument);
                }
                return sourceLength;
            default:
                throw new IllegalArgumentException(MSG_OPCODE + opcode);
        }
    }

    /**
     * Computes a single forward instruction output length.
     *
     * @param sourceLength source length
     * @param opcode       opcode
     * @param argument     argument
     * @return produced length
     */
    private static int computeSingleForwardAppliedLength(final int sourceLength, final char opcode,
            final char argument) {
        return computeSingleBackwardAppliedLength(sourceLength, opcode, argument);
    }

    /**
     * Computes output length for an empty source in backward traversal.
     *
     * @param patchCommand patch command
     * @return produced length
     */
    private static int computeBackwardEmptyAppliedLength(final String patchCommand) {
        int currentLength = 0;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);
            switch (opcode) {
                case INSERT_OPCODE:
                    currentLength++;
                    break;
                case SKIP_OPCODE:
                case REPLACE_OPCODE:
                case DELETE_OPCODE:
                    return 0;
                case NOOP_OPCODE:
                    if (argument != NOOP_ARGUMENT) {
                        throw new IllegalArgumentException(MSG_NOOP + argument);
                    }
                    return 0;
                default:
                    throw new IllegalArgumentException(MSG_OPCODE + opcode);
            }
        }
        return currentLength;
    }

    /**
     * Computes output length for an empty source in forward traversal.
     *
     * @param patchCommand patch command
     * @return produced length
     */
    private static int computeForwardEmptyAppliedLength(final String patchCommand) {
        return computeBackwardEmptyAppliedLength(patchCommand);
    }

    /**
     * Applies an already-sized patch into caller output.
     *
     * @param source             source text
     * @param sourceOffset       source offset
     * @param sourceLength       source length
     * @param patchCommand       patch command
     * @param traversalDirection traversal direction
     * @param output             output storage
     * @param outputOffset       output offset
     * @param producedLength     already-validated produced length
     */
    private static void applyToOutput(final CharSequence source, final int sourceOffset, final int sourceLength,
            final String patchCommand, final WordTraversalDirection traversalDirection, final char[] output,
            final int outputOffset, final int producedLength) {
        if (isPreservedSource(sourceLength, producedLength, patchCommand, traversalDirection)) {
            copySource(source, sourceOffset, sourceLength, output, outputOffset);
            return;
        }

        if (sourceLength > 0) {
            copySource(source, sourceOffset, sourceLength, output, outputOffset);
        }

        if (traversalDirection == WordTraversalDirection.BACKWARD) {
            applyBackwardToOutput(sourceLength, patchCommand, output, outputOffset);
        } else {
            applyForwardToOutput(sourceLength, patchCommand, output, outputOffset);
        }
    }

    /**
     * Applies an already-sized patch into caller output.
     *
     * @param source             source storage
     * @param sourceOffset       source offset
     * @param sourceLength       source length
     * @param patchCommand       patch command
     * @param traversalDirection traversal direction
     * @param output             output storage
     * @param outputOffset       output offset
     * @param producedLength     already-validated produced length
     */
    private static void applyToOutput(final char[] source, final int sourceOffset, final int sourceLength,
            final String patchCommand, final WordTraversalDirection traversalDirection, final char[] output,
            final int outputOffset, final int producedLength) {
        if (isPreservedSource(sourceLength, producedLength, patchCommand, traversalDirection)) {
            System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
            return;
        }

        if (sourceLength > 0) {
            System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
        }

        if (traversalDirection == WordTraversalDirection.BACKWARD) {
            applyBackwardToOutput(sourceLength, patchCommand, output, outputOffset);
        } else {
            applyForwardToOutput(sourceLength, patchCommand, output, outputOffset);
        }
    }

    /**
     * Determines whether the output is exactly the original source.
     *
     * @param sourceLength       source length
     * @param producedLength     produced length
     * @param patchCommand       patch command
     * @param traversalDirection traversal direction
     * @return {@code true} if copying the source is sufficient
     */
    private static boolean isPreservedSource(final int sourceLength, final int producedLength,
            final String patchCommand, final WordTraversalDirection traversalDirection) {
        return producedLength == sourceLength
                && isKnownPreserveOnlyPatch(sourceLength, patchCommand, traversalDirection);
    }

    /**
     * Returns whether equal length also means no mutation is needed.
     *
     * @param sourceLength       source length
     * @param patchCommand       patch command
     * @param traversalDirection traversal direction
     * @return {@code true} when the command preserves source content
     */
    private static boolean isKnownPreserveOnlyPatch(final int sourceLength, final String patchCommand,
            final WordTraversalDirection traversalDirection) {
        if (patchCommand == null || patchCommand.isEmpty() || NOOP_PATCH.equals(patchCommand)
                || (patchCommand.length() & 1) != 0) {
            return true;
        }
        if (patchCommand.length() == 2) {
            return isSingleInstructionPreserveOnly(sourceLength, patchCommand.charAt(0), patchCommand.charAt(1));
        }
        if (sourceLength == 0) {
            return hasEmptySourcePreserveOnlyPatch(patchCommand);
        }
        return traversalDirection == WordTraversalDirection.BACKWARD
                ? hasBackwardPreserveOnlyPatch(sourceLength, patchCommand)
                : hasForwardPreserveOnlyPatch(sourceLength, patchCommand);
    }

    /**
     * Tests whether a single instruction preserves the source content.
     *
     * @param sourceLength source length
     * @param opcode       opcode
     * @param argument     argument
     * @return {@code true} when no mutation should be applied
     */
    private static boolean isSingleInstructionPreserveOnly(final int sourceLength, final char opcode,
            final char argument) {
        switch (opcode) {
            case DELETE_OPCODE:
                final int encodedValue = decodeEncodedCount(argument);
                return encodedValue < 1 || encodedValue > sourceLength;
            case INSERT_OPCODE:
                return false;
            case REPLACE_OPCODE:
                return sourceLength == 0;
            case SKIP_OPCODE:
                return true;
            case NOOP_OPCODE:
                if (argument != NOOP_ARGUMENT) {
                    throw new IllegalArgumentException(MSG_NOOP + argument);
                }
                return true;
            default:
                throw new IllegalArgumentException(MSG_OPCODE + opcode);
        }
    }

    /**
     * Tests whether an empty-source patch preserves the source.
     *
     * @param patchCommand patch command
     * @return {@code true} when no mutation should be applied
     */
    private static boolean hasEmptySourcePreserveOnlyPatch(final String patchCommand) {
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);
            switch (opcode) {
                case INSERT_OPCODE:
                    break;
                case SKIP_OPCODE:
                case REPLACE_OPCODE:
                case DELETE_OPCODE:
                    return true;
                case NOOP_OPCODE:
                    if (argument != NOOP_ARGUMENT) {
                        throw new IllegalArgumentException(MSG_NOOP + argument);
                    }
                    return true;
                default:
                    throw new IllegalArgumentException(MSG_OPCODE + opcode);
            }
        }
        return false;
    }

    /**
     * Tests whether a backward patch preserves the source because it is malformed
     * or a NOOP.
     *
     * @param sourceLength source length
     * @param patchCommand patch command
     * @return {@code true} when no mutation should be applied
     */
    private static boolean hasBackwardPreserveOnlyPatch(final int sourceLength, final String patchCommand) {
        int currentLength = sourceLength;
        int position = sourceLength - 1;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);
            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = decodeEncodedCount(argument);
                    if (skipCount < 1) {
                        return true;
                    }
                    position = position - skipCount + 1;
                    break;
                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength) {
                        return true;
                    }
                    break;
                case DELETE_OPCODE:
                    final int deleteCount = decodeEncodedCount(argument);
                    if (deleteCount < 1) {
                        return true;
                    }
                    final int deleteEndExclusive = position + 1;
                    position -= deleteCount - 1;
                    if (position < 0 || deleteEndExclusive > currentLength || position > deleteEndExclusive) {
                        return true;
                    }
                    currentLength -= deleteEndExclusive - position;
                    break;
                case INSERT_OPCODE:
                    if (position < -1 || position >= currentLength) {
                        return true;
                    }
                    currentLength++;
                    position++;
                    break;
                case NOOP_OPCODE:
                    if (argument != NOOP_ARGUMENT) {
                        throw new IllegalArgumentException(MSG_NOOP + argument);
                    }
                    return true;
                default:
                    throw new IllegalArgumentException(MSG_OPCODE + opcode);
            }
            position--;
        }
        return false;
    }

    /**
     * Tests whether a forward patch preserves the source because it is malformed or
     * a NOOP.
     *
     * @param sourceLength source length
     * @param patchCommand patch command
     * @return {@code true} when no mutation should be applied
     */
    private static boolean hasForwardPreserveOnlyPatch(final int sourceLength, final String patchCommand) {
        int currentLength = sourceLength;
        int position = 0;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);
            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = decodeEncodedCount(argument);
                    if (skipCount < 1) {
                        return true;
                    }
                    position = position + skipCount - 1;
                    break;
                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength) {
                        return true;
                    }
                    break;
                case DELETE_OPCODE:
                    final int deleteCount = decodeEncodedCount(argument);
                    if (deleteCount < 1 || position < 0 || position + deleteCount > currentLength) {
                        return true;
                    }
                    currentLength -= deleteCount;
                    position--;
                    break;
                case INSERT_OPCODE:
                    if (position < 0 || position > currentLength) {
                        return true;
                    }
                    currentLength++;
                    break;
                case NOOP_OPCODE:
                    if (argument != NOOP_ARGUMENT) {
                        throw new IllegalArgumentException(MSG_NOOP + argument);
                    }
                    return true;
                default:
                    throw new IllegalArgumentException(MSG_OPCODE + opcode);
            }
            position++;
        }
        return false;
    }

    /**
     * Copies source characters from a sequence.
     *
     * @param source       source text
     * @param sourceOffset source offset
     * @param sourceLength source length
     * @param output       output storage
     * @param outputOffset output offset
     */
    private static void copySource(final CharSequence source, final int sourceOffset, final int sourceLength,
            final char[] output, final int outputOffset) {
        for (int index = 0; index < sourceLength; index++) {
            output[outputOffset + index] = source.charAt(sourceOffset + index);
        }
    }

    /**
     * Applies a backward patch after validation.
     *
     * @param sourceLength source length
     * @param patchCommand patch command
     * @param output       output storage initialized with source
     * @param outputOffset output offset
     */
    private static void applyBackwardToOutput(final int sourceLength, final String patchCommand, final char[] output,
            final int outputOffset) {
        if (sourceLength == 0) {
            applyBackwardEmptyToOutput(patchCommand, output, outputOffset);
            return;
        }

        int currentLength = sourceLength;
        int position = sourceLength - 1;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);

            switch (opcode) {
                case SKIP_OPCODE:
                    position = position - decodeEncodedCount(argument) + 1;
                    break;

                case REPLACE_OPCODE:
                    output[outputOffset + position] = argument;
                    break;

                case DELETE_OPCODE:
                    final int deleteEndExclusive = position + 1;
                    position -= decodeEncodedCount(argument) - 1;
                    System.arraycopy(output, outputOffset + deleteEndExclusive, output, outputOffset + position,
                            currentLength - deleteEndExclusive);
                    currentLength -= deleteEndExclusive - position;
                    break;

                case INSERT_OPCODE:
                    final int insertIndex = position + 1;
                    System.arraycopy(output, outputOffset + insertIndex, output, outputOffset + insertIndex + 1,
                            currentLength - insertIndex);
                    output[outputOffset + insertIndex] = argument;
                    currentLength++;
                    position++;
                    break;

                case NOOP_OPCODE:
                    return;

                default:
                    throw new AssertionError("Patch command was not validated.");
            }

            position--;
        }
    }

    /**
     * Applies a forward patch after validation.
     *
     * @param sourceLength source length
     * @param patchCommand patch command
     * @param output       output storage initialized with source
     * @param outputOffset output offset
     */
    private static void applyForwardToOutput(final int sourceLength, final String patchCommand, final char[] output,
            final int outputOffset) {
        if (sourceLength == 0) {
            applyForwardEmptyToOutput(patchCommand, output, outputOffset);
            return;
        }

        int currentLength = sourceLength;
        int position = 0;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char opcode = patchCommand.charAt(patchIndex);
            final char argument = patchCommand.charAt(patchIndex + 1);

            switch (opcode) {
                case SKIP_OPCODE:
                    position = position + decodeEncodedCount(argument) - 1;
                    break;

                case REPLACE_OPCODE:
                    output[outputOffset + position] = argument;
                    break;

                case DELETE_OPCODE:
                    final int deleteCount = decodeEncodedCount(argument);
                    System.arraycopy(output, outputOffset + position + deleteCount, output, outputOffset + position,
                            currentLength - position - deleteCount);
                    currentLength -= deleteCount;
                    position--;
                    break;

                case INSERT_OPCODE:
                    System.arraycopy(output, outputOffset + position, output, outputOffset + position + 1,
                            currentLength - position);
                    output[outputOffset + position] = argument;
                    currentLength++;
                    break;

                case NOOP_OPCODE:
                    return;

                default:
                    throw new AssertionError("Patch command was not validated.");
            }

            position++;
        }
    }

    /**
     * Applies an empty-source backward patch after validation.
     *
     * @param patchCommand patch command
     * @param output       output storage
     * @param outputOffset output offset
     */
    private static void applyBackwardEmptyToOutput(final String patchCommand, final char[] output,
            final int outputOffset) {
        int currentLength = 0;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            final char argument = patchCommand.charAt(patchIndex + 1);
            System.arraycopy(output, outputOffset, output, outputOffset + 1, currentLength);
            output[outputOffset] = argument;
            currentLength++;
        }
    }

    /**
     * Applies an empty-source forward patch after validation.
     *
     * @param patchCommand patch command
     * @param output       output storage
     * @param outputOffset output offset
     */
    private static void applyForwardEmptyToOutput(final String patchCommand, final char[] output,
            final int outputOffset) {
        int currentLength = 0;
        for (int patchIndex = 0, patchLength = patchCommand.length(); patchIndex < patchLength; patchIndex += 2) {
            output[outputOffset + currentLength] = patchCommand.charAt(patchIndex + 1);
            currentLength++;
        }
    }

    /**
     * Validates that source and output slices do not overlap when backed by the
     * same array.
     *
     * @param source       source storage
     * @param sourceOffset source offset
     * @param sourceLength source length
     * @param output       output storage
     * @param outputOffset output offset
     * @param outputLength output length
     */
    private static void validateNonOverlappingRanges(final char[] source, final int sourceOffset,
            final int sourceLength, final char[] output, final int outputOffset, final int outputLength) {
        if (!source.equals(output) || sourceLength == 0 || outputLength == 0) {
            return;
        }
        final int sourceEnd = sourceOffset + sourceLength;
        final int outputEnd = outputOffset + outputLength;
        if (sourceOffset < outputEnd && outputOffset < sourceEnd) {
            throw new IllegalArgumentException("source and output ranges must not overlap.");
        }
    }

    /**
     * Decodes a compact count argument used by skip and delete instructions.
     *
     * @param argument serialized count argument
     * @return decoded positive count, or {@code -1} when the argument is malformed
     */
    private static int decodeEncodedCount(final char argument) {
        if (argument < 'a') {
            return -1;
        }
        return argument - 'a' + 1;
    }

    /**
     * Ensures that internal matrices are large enough for the requested input
     * dimensions.
     *
     * @param requiredSourceCapacity required source dimension
     * @param requiredTargetCapacity required target dimension
     */
    private void ensureCapacity(final int requiredSourceCapacity, final int requiredTargetCapacity) {
        if (requiredSourceCapacity <= this.sourceCapacity && requiredTargetCapacity <= this.targetCapacity) {
            return;
        }

        this.sourceCapacity = Math.max(this.sourceCapacity, requiredSourceCapacity) + CAPACITY_MARGIN;
        this.targetCapacity = Math.max(this.targetCapacity, requiredTargetCapacity) + CAPACITY_MARGIN;

        this.costMatrix = new int[this.sourceCapacity][this.targetCapacity];
        this.traceMatrix = new Trace[this.sourceCapacity][this.targetCapacity];
    }

    /**
     * Initializes the first row and first column of the dynamic-programming
     * matrices.
     *
     * @param sourceLength length of the source word
     * @param targetLength length of the target word
     */
    private void initializeBoundaryConditionsBackward(final int sourceLength, final int targetLength) {
        this.costMatrix[0][0] = 0;
        this.traceMatrix[0][0] = Trace.MATCH;

        for (int sourceIndex = 1; sourceIndex <= sourceLength; sourceIndex++) {
            this.costMatrix[sourceIndex][0] = sourceIndex * this.deleteCost;
            this.traceMatrix[sourceIndex][0] = Trace.DELETE;
        }

        for (int targetIndex = 1; targetIndex <= targetLength; targetIndex++) {
            this.costMatrix[0][targetIndex] = targetIndex * this.insertCost;
            this.traceMatrix[0][targetIndex] = Trace.INSERT;
        }
    }

    /**
     * Initializes boundary conditions for forward dynamic-programming traversal.
     *
     * @param sourceLength length of the source word
     * @param targetLength length of the target word
     */
    private void initializeBoundaryConditionsForward(final int sourceLength, final int targetLength) {
        this.costMatrix[sourceLength][targetLength] = 0;
        this.traceMatrix[sourceLength][targetLength] = Trace.MATCH;

        for (int sourceIndex = sourceLength - 1; sourceIndex >= 0; sourceIndex--) {
            this.costMatrix[sourceIndex][targetLength] = this.costMatrix[sourceIndex + 1][targetLength]
                    + this.deleteCost;
            this.traceMatrix[sourceIndex][targetLength] = Trace.DELETE;
        }

        for (int targetIndex = targetLength - 1; targetIndex >= 0; targetIndex--) {
            this.costMatrix[sourceLength][targetIndex] = this.costMatrix[sourceLength][targetIndex + 1]
                    + this.insertCost;
            this.traceMatrix[sourceLength][targetIndex] = Trace.INSERT;
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
     * @param direction        traversal direction used to compare characters
     */
    private void fillMatrices(final char[] sourceCharacters, final char[] targetCharacters, final int sourceLength,
            final int targetLength, final WordTraversalDirection direction) {
        final int sourceStart;
        final int sourceEndExclusive;
        final int sourceStep;
        final int targetStart;
        final int targetEndExclusive;
        final int targetStep;
        final int sourceCharacterOffset;
        final int targetCharacterOffset;
        final int sourceNeighborDelta;
        final int targetNeighborDelta;

        if (direction == WordTraversalDirection.BACKWARD) {
            sourceStart = 1;
            sourceEndExclusive = sourceLength + 1;
            sourceStep = 1;
            targetStart = 1;
            targetEndExclusive = targetLength + 1;
            targetStep = 1;
            sourceCharacterOffset = -1;
            targetCharacterOffset = -1;
            sourceNeighborDelta = -1;
            targetNeighborDelta = -1;
        } else {
            sourceStart = sourceLength - 1;
            sourceEndExclusive = -1;
            sourceStep = -1;
            targetStart = targetLength - 1;
            targetEndExclusive = -1;
            targetStep = -1;
            sourceCharacterOffset = 0;
            targetCharacterOffset = 0;
            sourceNeighborDelta = 1;
            targetNeighborDelta = 1;
        }

        for (int sourceIndex = sourceStart; sourceIndex != sourceEndExclusive; sourceIndex += sourceStep) {
            final char sourceCharacter = sourceCharacters[sourceIndex + sourceCharacterOffset];
            final int sourceNeighbor = sourceIndex + sourceNeighborDelta;

            for (int targetIndex = targetStart; targetIndex != targetEndExclusive; targetIndex += targetStep) {
                final char targetCharacter = targetCharacters[targetIndex + targetCharacterOffset];
                final int targetNeighbor = targetIndex + targetNeighborDelta;

                final int deleteCandidate = this.costMatrix[sourceNeighbor][targetIndex] + this.deleteCost;
                final int insertCandidate = this.costMatrix[sourceIndex][targetNeighbor] + this.insertCost;
                final int replaceCandidate = this.costMatrix[sourceNeighbor][targetNeighbor] + this.replaceCost;
                final int matchCandidate = this.costMatrix[sourceNeighbor][targetNeighbor]
                        + (sourceCharacter == targetCharacter ? this.matchCost : MISMATCH_PENALTY);

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

                this.costMatrix[sourceIndex][targetIndex] = bestCost;
                this.traceMatrix[sourceIndex][targetIndex] = bestTrace;
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
    private String buildPatchCommandBackward(final char[] targetCharacters, final int sourceLength,
            final int targetLength) {
        final StringBuilder patchBuilder = new StringBuilder(sourceLength + targetLength);

        char pendingDeletes = COUNT_SENTINEL;
        char pendingSkips = COUNT_SENTINEL;

        int sourceIndex = sourceLength;
        int targetIndex = targetLength;

        while (sourceIndex != 0 || targetIndex != 0) {
            final Trace trace = this.traceMatrix[sourceIndex][targetIndex];

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
     * Reconstructs compact patch command for forward traversal.
     *
     * @param targetCharacters target characters
     * @param sourceLength     source length
     * @param targetLength     target length
     * @return compact patch command
     */
    private String buildPatchCommandForward(final char[] targetCharacters, final int sourceLength,
            final int targetLength) {
        final StringBuilder patchBuilder = new StringBuilder(sourceLength + targetLength);

        char pendingDeletes = COUNT_SENTINEL;
        char pendingSkips = COUNT_SENTINEL;

        int sourceIndex = 0;
        int targetIndex = 0;

        while (sourceIndex != sourceLength || targetIndex != targetLength) {
            final Trace trace = this.traceMatrix[sourceIndex][targetIndex];

            switch (trace) {
                case DELETE:
                    if (pendingSkips != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, SKIP_OPCODE, pendingSkips);
                        pendingSkips = COUNT_SENTINEL;
                    }
                    pendingDeletes++;
                    sourceIndex++;
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
                    appendInstruction(patchBuilder, INSERT_OPCODE, targetCharacters[targetIndex]);
                    targetIndex++;
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
                    appendInstruction(patchBuilder, REPLACE_OPCODE, targetCharacters[targetIndex]);
                    sourceIndex++;
                    targetIndex++;
                    break;

                case MATCH:
                    if (pendingDeletes != COUNT_SENTINEL) {
                        appendInstruction(patchBuilder, DELETE_OPCODE, pendingDeletes);
                        pendingDeletes = COUNT_SENTINEL;
                    }
                    pendingSkips++;
                    sourceIndex++;
                    targetIndex++;
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
    private static void appendInstruction(final StringBuilder patchBuilder, final char opcode, final char argument) {
        patchBuilder.append(opcode).append(argument);
    }

    /**
     * Fluent builder for creating direction-specialized {@link PatchCommandEncoder}
     * instances.
     */
    public static final class Builder {
        private WordTraversalDirection traversalDirection = WordTraversalDirection.BACKWARD;
        private int insertCost = 1;
        private int deleteCost = 1;
        private int replaceCost = 1;
        private int matchCost; // = 0

        /**
         * Creates a builder initialized with the default Egothor-compatible cost model
         * and backward traversal.
         */
        public Builder() {
            // Default values are assigned in field initializers.
        }

        /**
         * Sets traversal direction used by the created encoder.
         *
         * @param value traversal direction
         * @return this builder
         */
        public Builder traversalDirection(final WordTraversalDirection value) {
            this.traversalDirection = Objects.requireNonNull(value, "traversalDirection");
            return this;
        }

        /**
         * Sets cost of an insert operation.
         * 
         * @param value cost of the operation
         * @return this builder
         */
        public Builder insertCost(final int value) {
            this.insertCost = value;
            return this;
        }

        /**
         * Sets cost of a delete operation.
         * 
         * @param value cost of the operation
         * @return this builder
         */
        public Builder deleteCost(final int value) {
            this.deleteCost = value;
            return this;
        }

        /**
         * Sets cost of a replace operation.
         * 
         * @param value cost of the operation
         * @return this builder
         */
        public Builder replaceCost(final int value) {
            this.replaceCost = value;
            return this;
        }

        /**
         * Sets cost of a match operation.
         * 
         * @param value cost of the operation
         * @return this builder
         */
        public Builder matchCost(final int value) {
            this.matchCost = value;
            return this;
        }

        /**
         * Builds a direction-specialized encoder instance.
         *
         * @return configured encoder
         */
        public PatchCommandEncoder build() {
            return new PatchCommandEncoder(this);
        }
    }
}
