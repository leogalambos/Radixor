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

/**
 * Immutable runtime command produced from one serialized Radixor patch command.
 *
 * <p>
 * Compilation selects a concrete command class for the patch shape. Common
 * one-operation commands such as suffix deletion, prefix deletion, character
 * append, character prepend, and single-character replacement therefore execute
 * without a per-application opcode switch. Multi-operation patches are represented
 * as a compound command containing concrete atomic operations.
 * </p>
 *
 * <p>
 * Instances are immutable and thread-safe. Setup code may cache and share them
 * freely across tries and benchmark states.
 * </p>
 */
@SuppressWarnings({ "PMD.CyclomaticComplexity", "PMD.UseVarargs" })
public abstract class CompiledPatchCommand {

    /**
     * Return value used when the caller-owned output range is too small.
     */
    public static final int APPLY_INSUFFICIENT_CAPACITY = PatchCommandEncoder.APPLY_INSUFFICIENT_CAPACITY;

    /**
     * Opcode for deleting one or more characters.
     */
    private static final char DELETE_OPCODE = 'D';

    /**
     * Opcode for inserting one character.
     */
    private static final char INSERT_OPCODE = 'I';

    /**
     * Opcode for replacing one character.
     */
    private static final char REPLACE_OPCODE = 'R';

    /**
     * Opcode for skipping one or more unchanged characters.
     */
    private static final char SKIP_OPCODE = '-';

    /**
     * Opcode for a canonical no-operation patch.
     */
    private static final char NOOP_OPCODE = 'N';

    /**
     * Canonical no-operation patch argument.
     */
    private static final char NOOP_ARGUMENT = 'a';

    /**
     * Serialized length of one opcode/argument patch command.
     */
    private static final int SINGLE_COMMAND_LENGTH = 2;

    /**
     * Smallest decoded skip/delete count accepted by the patch format.
     */
    private static final int MINIMUM_COUNT = 1;

    /**
     * First encoded count argument.
     */
    private static final char FIRST_COUNT_ARGUMENT = 'a';

    /**
     * Prefix used in unsupported NOOP patch argument exceptions.
     */
    private static final String MSG_NOOP = "Unsupported NOOP patch argument: ";

    /**
     * Prefix used in unsupported patch opcode exceptions.
     */
    private static final String MSG_OPCODE = "Unsupported patch opcode: ";

    /**
     * Traversal direction used by this command.
     */
    private final WordTraversalDirection traversalDirection;

    /**
     * Constant result-length delta applied by this command.
     */
    private final int lengthDelta;

    /**
     * Minimum source length required before this command can be applied.
     */
    private final int minimumSourceLength;

    /**
     * Creates one compiled command.
     *
     * @param traversalDirection traversal direction used by this command
     * @param lengthDelta        constant result-length delta for this command
     * @param minimumSourceLength minimum source length required for application
     */
    protected CompiledPatchCommand(final WordTraversalDirection traversalDirection, final int lengthDelta,
            final int minimumSourceLength) {
        this.traversalDirection = Objects.requireNonNull(traversalDirection, "traversalDirection");
        this.lengthDelta = lengthDelta;
        this.minimumSourceLength = minimumSourceLength;
    }

    /**
     * Creates a builder that compiles one serialized patch command.
     *
     * @param patchCommand       serialized patch command, or {@code null} for a
     *                           preserve-only command
     * @param traversalDirection traversal direction used by the patch command
     * @return builder configured for the supplied command
     * @throws NullPointerException if {@code traversalDirection} is {@code null}
     */
    public static Builder builder(final String patchCommand, final WordTraversalDirection traversalDirection) {
        return new Builder(patchCommand, traversalDirection);
    }

    /**
     * Compiles a serialized patch command for repeated application.
     *
     * @param patchCommand       serialized patch command, or {@code null} for a
     *                           preserve-only command
     * @param traversalDirection traversal direction used by the patch command
     * @return immutable compiled patch command
     * @throws NullPointerException     if {@code traversalDirection} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the serialized command contains an
     *                                  unsupported opcode or invalid NOOP argument
     */
    public static CompiledPatchCommand compile(final String patchCommand,
            final WordTraversalDirection traversalDirection) {
        return builder(patchCommand, traversalDirection).build();
    }

    /**
     * Applies this command to one source word and returns the transformed word.
     *
     * @param source source word
     * @return transformed word, or {@code null} when {@code source} is
     *         {@code null}
     */
    public final String apply(final String source) {
        if (source == null) {
            return null;
        }
        return applyNonNull(source);
    }

    /**
     * Applies this command from a character sequence into caller-owned output
     * storage.
     *
     * @param source       source text
     * @param output       output storage
     * @param outputOffset first writable output offset
     * @param outputLength writable output capacity
     * @return produced character count, or {@link #APPLY_INSUFFICIENT_CAPACITY}
     *         when {@code outputLength} is too small
     * @throws NullPointerException      if {@code source} or {@code output} is
     *                                   {@code null}
     * @throws IndexOutOfBoundsException if the output range is invalid
     */
    public final int applyTo(final CharSequence source, final char[] output, final int outputOffset,
            final int outputLength) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        Objects.checkFromIndexSize(outputOffset, outputLength, output.length);
        return applyTo(source, 0, source.length(), output, outputOffset, outputLength);
    }

    /**
     * Applies this command from a character-sequence slice into caller-owned output
     * storage.
     *
     * @param source       source text
     * @param sourceOffset first source offset
     * @param sourceLength number of source characters
     * @param output       output storage
     * @param outputOffset first writable output offset
     * @param outputLength writable output capacity
     * @return produced character count, or {@link #APPLY_INSUFFICIENT_CAPACITY}
     *         when {@code outputLength} is too small
     * @throws NullPointerException      if {@code source} or {@code output} is
     *                                   {@code null}
     * @throws IndexOutOfBoundsException if any range is invalid
     */
    public final int applyTo(final CharSequence source, final int sourceOffset, final int sourceLength,
            final char[] output, final int outputOffset, final int outputLength) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        Objects.checkFromIndexSize(sourceOffset, sourceLength, source.length());
        Objects.checkFromIndexSize(outputOffset, outputLength, output.length);

        final int producedLength = computeAppliedLength(sourceLength);
        if (producedLength > outputLength) {
            return APPLY_INSUFFICIENT_CAPACITY;
        }
        applySequenceToOutput(source, sourceOffset, sourceLength, output, outputOffset, producedLength);
        return producedLength;
    }

    /**
     * Applies this command from a character-array slice into caller-owned output
     * storage.
     *
     * @param source       source storage
     * @param sourceOffset first source offset
     * @param sourceLength number of source characters
     * @param output       output storage
     * @param outputOffset first writable output offset
     * @param outputLength writable output capacity
     * @return produced character count, or {@link #APPLY_INSUFFICIENT_CAPACITY}
     *         when {@code outputLength} is too small
     * @throws NullPointerException      if {@code source} or {@code output} is
     *                                   {@code null}
     * @throws IndexOutOfBoundsException if any range is invalid
     * @throws IllegalArgumentException  if source and output ranges overlap in the
     *                                   same array
     */
    public final int applyTo(final char[] source, final int sourceOffset, final int sourceLength,
            final char[] output, final int outputOffset, final int outputLength) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        Objects.checkFromIndexSize(sourceOffset, sourceLength, source.length);
        Objects.checkFromIndexSize(outputOffset, outputLength, output.length);
        validateNonOverlappingRanges(source, sourceOffset, sourceLength, output, outputOffset, outputLength);

        final int producedLength = computeAppliedLength(sourceLength);
        if (producedLength > outputLength) {
            return APPLY_INSUFFICIENT_CAPACITY;
        }
        applyArrayToOutput(source, sourceOffset, sourceLength, output, outputOffset, producedLength);
        return producedLength;
    }

    /**
     * Returns this command traversal direction.
     *
     * @return traversal direction
     */
    public final WordTraversalDirection traversalDirection() {
        return this.traversalDirection;
    }

    /**
     * Reports whether this command preserves every non-null source unchanged.
     *
     * <p>
     * Hot paths can use this method to avoid output-buffer copying and result-string
     * allocation for canonical no-operation dictionary entries.
     * </p>
     *
     * @return {@code true} when {@link #apply(String)} always returns the supplied
     *         source reference for non-null input
     */
    public abstract boolean preservesAllSources();

    /**
     * Applies this command to a non-null source string.
     *
     * @param source non-null source word
     * @return transformed word
     */
    protected abstract String applyNonNull(String source);

    /**
     * Computes the output length for a source of the supplied length.
     *
     * @param sourceLength source length
     * @return output length
     */
    protected final int computeAppliedLength(final int sourceLength) {
        if (sourceLength < this.minimumSourceLength) {
            return sourceLength;
        }
        final int appliedLength = sourceLength + this.lengthDelta;
        return appliedLength < MINIMUM_COUNT ? sourceLength : appliedLength;
    }

    /**
     * Returns whether this command can produce a non-empty result for the supplied
     * source length.
     *
     * @param sourceLength source length
     * @return {@code true} when the constant command delta keeps the result
     *         non-empty
     */
    protected final boolean hasApplicableLength(final int sourceLength) {
        return sourceLength >= this.minimumSourceLength && sourceLength + this.lengthDelta >= MINIMUM_COUNT;
    }

    /**
     * Applies this command from a character sequence into caller-owned output.
     *
     * @param source         source text
     * @param sourceOffset   first source offset
     * @param sourceLength   source length
     * @param output         output storage
     * @param outputOffset   first output offset
     * @param producedLength computed produced length
     */
    protected abstract void applySequenceToOutput(CharSequence source, int sourceOffset, int sourceLength,
            char[] output, int outputOffset, int producedLength);

    /**
     * Applies this command from a character array into caller-owned output.
     *
     * @param source         source storage
     * @param sourceOffset   first source offset
     * @param sourceLength   source length
     * @param output         output storage
     * @param outputOffset   first output offset
     * @param producedLength computed produced length
     */
    protected abstract void applyArrayToOutput(char[] source, int sourceOffset, int sourceLength,
            char[] output, int outputOffset, int producedLength);

    /**
     * Builder that compiles one serialized patch command to the most specific
     * runtime command class available.
     */
    public static final class Builder {

        /**
         * Serialized patch command.
         */
        private final String patchCommand;

        /**
         * Traversal direction for the command.
         */
        private final WordTraversalDirection traversalDirection;

        private Builder(final String patchCommand, final WordTraversalDirection traversalDirection) {
            this.patchCommand = patchCommand;
            this.traversalDirection = Objects.requireNonNull(traversalDirection, "traversalDirection");
        }

        /**
         * Builds the concrete command instance.
         *
         * @return compiled command instance
         * @throws IllegalArgumentException if the serialized command contains an
         *                                  unsupported opcode or invalid NOOP
         *                                  argument
         */
        public CompiledPatchCommand build() {
            if (this.patchCommand == null) {
                return preserve(this.traversalDirection);
            }

            final int patchLength = this.patchCommand.length();
            if (patchLength == 0 || (patchLength & 1) != 0) {
                return preserve(this.traversalDirection);
            }
            if (patchLength == SINGLE_COMMAND_LENGTH) {
                return compileSingle(this.patchCommand.charAt(0), this.patchCommand.charAt(1),
                        this.traversalDirection);
            }

            final int operationCount = patchLength >> 1;
            final char[] opcodes = new char[operationCount];
            final int[] operands = new int[operationCount];
            for (int patchIndex = 0; patchIndex < patchLength; patchIndex += SINGLE_COMMAND_LENGTH) {
                final int operationIndex = patchIndex >> 1;
                final char opcode = this.patchCommand.charAt(patchIndex);
                final int operand = compileOperand(opcode, this.patchCommand.charAt(patchIndex + 1));
                if (operand < 0) {
                    return preserve(this.traversalDirection);
                }
                opcodes[operationIndex] = opcode;
                operands[operationIndex] = operand;
            }

            final int lengthDelta = computeLengthDelta(opcodes, operands);
            final int minimumSourceLength = this.traversalDirection == WordTraversalDirection.BACKWARD
                    ? computeBackwardMinimumSourceLength(opcodes, operands)
                    : computeForwardMinimumSourceLength(opcodes, operands);
            return this.traversalDirection == WordTraversalDirection.BACKWARD
                    ? new BackwardCompoundCommand(this.traversalDirection, opcodes, operands, lengthDelta,
                            minimumSourceLength)
                    : new ForwardCompoundCommand(this.traversalDirection, opcodes, operands, lengthDelta,
                            minimumSourceLength);
        }
    }

    private static CompiledPatchCommand preserve(final WordTraversalDirection traversalDirection) {
        return new PreserveCommand(traversalDirection);
    }

    private static CompiledPatchCommand compileSingle(final char opcode, final char argument,
            final WordTraversalDirection traversalDirection) {
        switch (opcode) {
            case DELETE_OPCODE:
                final int deleteCount = decodeEncodedCount(argument);
                if (deleteCount < MINIMUM_COUNT) {
                    return preserve(traversalDirection);
                }
                return traversalDirection == WordTraversalDirection.BACKWARD
                        ? new DeleteSuffixCommand(traversalDirection, deleteCount)
                        : new DeletePrefixCommand(traversalDirection, deleteCount);
            case INSERT_OPCODE:
                return traversalDirection == WordTraversalDirection.BACKWARD
                        ? new AppendCharacterCommand(traversalDirection, argument)
                        : new PrependCharacterCommand(traversalDirection, argument);
            case REPLACE_OPCODE:
                return traversalDirection == WordTraversalDirection.BACKWARD
                        ? new ReplaceLastCharacterCommand(traversalDirection, argument)
                        : new ReplaceFirstCharacterCommand(traversalDirection, argument);
            case SKIP_OPCODE:
                return preserve(traversalDirection);
            case NOOP_OPCODE:
                if (argument != NOOP_ARGUMENT) {
                    throw new IllegalArgumentException(MSG_NOOP + argument);
                }
                return preserve(traversalDirection);
            default:
                throw new IllegalArgumentException(MSG_OPCODE + opcode);
        }
    }

    private static int compileOperand(final char opcode, final char argument) {
        switch (opcode) {
            case SKIP_OPCODE:
                final int skipCount = decodeEncodedCount(argument);
                return skipCount < MINIMUM_COUNT ? -1 : skipCount;
            case DELETE_OPCODE:
                final int deleteCount = decodeEncodedCount(argument);
                return deleteCount < MINIMUM_COUNT ? -1 : deleteCount;
            case INSERT_OPCODE:
            case REPLACE_OPCODE:
                return argument;
            case NOOP_OPCODE:
                if (argument != NOOP_ARGUMENT) {
                    throw new IllegalArgumentException(MSG_NOOP + argument);
                }
                return -1;
            default:
                throw new IllegalArgumentException(MSG_OPCODE + opcode);
        }
    }

    private static int computeLengthDelta(final char[] opcodes, final int[] operands) {
        int lengthDelta = 0;
        for (int index = 0; index < opcodes.length; index++) {
            switch (opcodes[index]) {
                case DELETE_OPCODE:
                    lengthDelta -= operands[index];
                    break;
                case INSERT_OPCODE:
                    lengthDelta++;
                    break;
                case SKIP_OPCODE:
                case REPLACE_OPCODE:
                    break;
                default:
                    throw new AssertionError(MSG_OPCODE + opcodes[index]);
            }
        }
        return lengthDelta;
    }

    private static int computeForwardMinimumSourceLength(final char[] opcodes, final int[] operands) {
        int minimumSourceLength = 0;
        int position = 0;
        int lengthDelta = 0;
        for (int index = 0; index < opcodes.length; index++) {
            final int operand = operands[index];
            switch (opcodes[index]) {
                case SKIP_OPCODE:
                    position += operand;
                    break;
                case DELETE_OPCODE:
                    minimumSourceLength = Math.max(minimumSourceLength, position + operand - lengthDelta);
                    lengthDelta -= operand;
                    break;
                case INSERT_OPCODE:
                    minimumSourceLength = Math.max(minimumSourceLength, position - lengthDelta);
                    lengthDelta++;
                    position++;
                    break;
                case REPLACE_OPCODE:
                    minimumSourceLength = Math.max(minimumSourceLength, position + 1 - lengthDelta);
                    position++;
                    break;
                default:
                    throw new AssertionError(MSG_OPCODE + opcodes[index]);
            }
        }
        return minimumSourceLength;
    }

    private static int computeBackwardMinimumSourceLength(final char[] opcodes, final int[] operands) {
        int minimumSourceLength = 0;
        int consumedFromEnd = 0;
        for (int index = 0; index < opcodes.length; index++) {
            final int operand = operands[index];
            switch (opcodes[index]) {
                case SKIP_OPCODE:
                    consumedFromEnd += operand;
                    break;
                case DELETE_OPCODE:
                    minimumSourceLength = Math.max(minimumSourceLength, consumedFromEnd + operand);
                    consumedFromEnd += operand;
                    break;
                case INSERT_OPCODE:
                    minimumSourceLength = Math.max(minimumSourceLength, consumedFromEnd);
                    break;
                case REPLACE_OPCODE:
                    minimumSourceLength = Math.max(minimumSourceLength, consumedFromEnd + 1);
                    consumedFromEnd++;
                    break;
                default:
                    throw new AssertionError(MSG_OPCODE + opcodes[index]);
            }
        }
        return minimumSourceLength;
    }

    private static int decodeEncodedCount(final char argument) {
        if (argument < FIRST_COUNT_ARGUMENT) {
            return -1;
        }
        return argument - FIRST_COUNT_ARGUMENT + MINIMUM_COUNT;
    }

    private static void copySource(final CharSequence source, final int sourceOffset, final int sourceLength,
            final char[] output, final int outputOffset) {
        if (sourceLength <= 0) {
            return;
        }
        if (source instanceof String sourceString) {
            sourceString.getChars(sourceOffset, sourceOffset + sourceLength, output, outputOffset);
            return;
        }
        for (int index = 0; index < sourceLength; index++) {
            output[outputOffset + index] = source.charAt(sourceOffset + index);
        }
    }

    private static void validateNonOverlappingRanges(final char[] source, final int sourceOffset,
            final int sourceLength, final char[] output, final int outputOffset, final int outputLength) {
        if (!sameArray(source, output) || sourceLength == 0 || outputLength == 0) {
            return;
        }
        final int sourceEnd = sourceOffset + sourceLength;
        final int outputEnd = outputOffset + outputLength;
        if (sourceOffset < outputEnd && outputOffset < sourceEnd) {
            throw new IllegalArgumentException("source and output ranges must not overlap.");
        }
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private static boolean sameArray(final char[] left, final char[] right) {
        return left == right;
    }

    /**
     * Command that preserves the source unchanged.
     */
    private static final class PreserveCommand extends CompiledPatchCommand {

        private PreserveCommand(final WordTraversalDirection traversalDirection) {
            super(traversalDirection, 0, 0);
        }

        @Override
        protected String applyNonNull(final String source) {
            return source;
        }

        @Override
        public boolean preservesAllSources() {
            return true;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            copySource(source, sourceOffset, sourceLength, output, outputOffset);
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
        }
    }

    /**
     * Command that deletes characters from the logical suffix of a backward patch.
     */
    private static final class DeleteSuffixCommand extends CompiledPatchCommand {

        /**
         * Number of suffix characters deleted.
         */
        private final int count;

        private DeleteSuffixCommand(final WordTraversalDirection traversalDirection, final int count) {
            super(traversalDirection, -count, 0);
            this.count = count;
        }

        @Override
        protected String applyNonNull(final String source) {
            final int sourceLength = source.length();
            if (!hasApplicableLength(sourceLength)) {
                return source;
            }
            return source.substring(0, sourceLength - this.count);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            copySource(source, sourceOffset, producedLength, output, outputOffset);
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            System.arraycopy(source, sourceOffset, output, outputOffset, producedLength);
        }
    }

    /**
     * Command that deletes characters from the logical prefix of a forward patch.
     */
    private static final class DeletePrefixCommand extends CompiledPatchCommand {

        /**
         * Number of prefix characters deleted.
         */
        private final int count;

        private DeletePrefixCommand(final WordTraversalDirection traversalDirection, final int count) {
            super(traversalDirection, -count, 0);
            this.count = count;
        }

        @Override
        protected String applyNonNull(final String source) {
            if (!hasApplicableLength(source.length())) {
                return source;
            }
            return source.substring(this.count);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            final int effectiveOffset = producedLength == sourceLength ? sourceOffset : sourceOffset + this.count;
            copySource(source, effectiveOffset, producedLength, output, outputOffset);
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            final int effectiveOffset = producedLength == sourceLength ? sourceOffset : sourceOffset + this.count;
            System.arraycopy(source, effectiveOffset, output, outputOffset, producedLength);
        }
    }

    /**
     * Command that appends one character to a backward patch result.
     */
    private static final class AppendCharacterCommand extends CompiledPatchCommand {

        /**
         * Appended character.
         */
        private final char character;

        private AppendCharacterCommand(final WordTraversalDirection traversalDirection, final char character) {
            super(traversalDirection, 1, 0);
            this.character = character;
        }

        @Override
        protected String applyNonNull(final String source) {
            final int sourceLength = source.length();
            final char[] target = new char[sourceLength + 1];
            source.getChars(0, sourceLength, target, 0);
            target[sourceLength] = this.character;
            return new String(target);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            copySource(source, sourceOffset, sourceLength, output, outputOffset);
            output[outputOffset + sourceLength] = this.character;
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
            output[outputOffset + sourceLength] = this.character;
        }
    }

    /**
     * Command that prepends one character to a forward patch result.
     */
    private static final class PrependCharacterCommand extends CompiledPatchCommand {

        /**
         * Prepended character.
         */
        private final char character;

        private PrependCharacterCommand(final WordTraversalDirection traversalDirection, final char character) {
            super(traversalDirection, 1, 0);
            this.character = character;
        }

        @Override
        protected String applyNonNull(final String source) {
            final int sourceLength = source.length();
            final char[] target = new char[sourceLength + 1];
            target[0] = this.character;
            source.getChars(0, sourceLength, target, 1);
            return new String(target);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            output[outputOffset] = this.character;
            copySource(source, sourceOffset, sourceLength, output, outputOffset + 1);
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            output[outputOffset] = this.character;
            System.arraycopy(source, sourceOffset, output, outputOffset + 1, sourceLength);
        }
    }

    /**
     * Command that replaces the final character of a backward patch result.
     */
    private static final class ReplaceLastCharacterCommand extends CompiledPatchCommand {

        /**
         * Replacement character.
         */
        private final char character;

        private ReplaceLastCharacterCommand(final WordTraversalDirection traversalDirection, final char character) {
            super(traversalDirection, 0, 1);
            this.character = character;
        }

        @Override
        protected String applyNonNull(final String source) {
            final int sourceLength = source.length();
            if (sourceLength == 0) {
                return source;
            }
            final char[] target = source.toCharArray();
            target[sourceLength - 1] = this.character;
            return new String(target);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            copySource(source, sourceOffset, sourceLength, output, outputOffset);
            if (sourceLength > 0) {
                output[outputOffset + sourceLength - 1] = this.character;
            }
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
            if (sourceLength > 0) {
                output[outputOffset + sourceLength - 1] = this.character;
            }
        }
    }

    /**
     * Command that replaces the first character of a forward patch result.
     */
    private static final class ReplaceFirstCharacterCommand extends CompiledPatchCommand {

        /**
         * Replacement character.
         */
        private final char character;

        private ReplaceFirstCharacterCommand(final WordTraversalDirection traversalDirection, final char character) {
            super(traversalDirection, 0, 1);
            this.character = character;
        }

        @Override
        protected String applyNonNull(final String source) {
            if (source.isEmpty()) {
                return source;
            }
            final char[] target = source.toCharArray();
            target[0] = this.character;
            return new String(target);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            copySource(source, sourceOffset, sourceLength, output, outputOffset);
            if (sourceLength > 0) {
                output[outputOffset] = this.character;
            }
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
            if (sourceLength > 0) {
                output[outputOffset] = this.character;
            }
        }
    }

    /**
     * Compound command that applies atomic operations in backward traversal order.
     */
    private static final class BackwardCompoundCommand extends CompiledPatchCommand {

        /**
         * Operation opcodes in serialized order.
         */
        private final char[] opcodes;

        /**
         * Operation counts or character operands in serialized order.
         */
        private final int[] operands;

        private BackwardCompoundCommand(final WordTraversalDirection traversalDirection, final char[] opcodes,
                final int[] operands, final int lengthDelta, final int minimumSourceLength) {
            super(traversalDirection, lengthDelta, minimumSourceLength);
            this.opcodes = opcodes;
            this.operands = operands;
        }

        @Override
        protected String applyNonNull(final String source) {
            final int sourceLength = source.length();
            if (!hasApplicableLength(sourceLength)) {
                return source;
            }
            final int producedLength = computeAppliedLength(sourceLength);
            final char[] target = new char[producedLength];
            writeSequence(source, 0, sourceLength, target, 0, producedLength);
            return new String(target);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            writeSequence(source, sourceOffset, sourceLength, output, outputOffset, producedLength);
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            writeArray(source, sourceOffset, sourceLength, output, outputOffset, producedLength);
        }

        private void writeSequence(final CharSequence source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            if (!writeBackwardSequence(this.opcodes, this.operands, source, sourceOffset, sourceLength, output,
                    outputOffset, producedLength)) {
                copySource(source, sourceOffset, sourceLength, output, outputOffset);
            }
        }

        private void writeArray(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            if (!writeBackwardArray(this.opcodes, this.operands, source, sourceOffset, sourceLength, output,
                    outputOffset, producedLength)) {
                System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
            }
        }
    }

    /**
     * Compound command that applies atomic operations in forward traversal order.
     */
    private static final class ForwardCompoundCommand extends CompiledPatchCommand {

        /**
         * Operation opcodes in serialized order.
         */
        private final char[] opcodes;

        /**
         * Operation counts or character operands in serialized order.
         */
        private final int[] operands;

        private ForwardCompoundCommand(final WordTraversalDirection traversalDirection, final char[] opcodes,
                final int[] operands, final int lengthDelta, final int minimumSourceLength) {
            super(traversalDirection, lengthDelta, minimumSourceLength);
            this.opcodes = opcodes;
            this.operands = operands;
        }

        @Override
        protected String applyNonNull(final String source) {
            final int sourceLength = source.length();
            if (!hasApplicableLength(sourceLength)) {
                return source;
            }
            final int producedLength = computeAppliedLength(sourceLength);
            final char[] target = new char[producedLength];
            writeSequence(source, 0, sourceLength, target, 0, producedLength);
            return new String(target);
        }

        @Override
        public boolean preservesAllSources() {
            return false;
        }

        @Override
        protected void applySequenceToOutput(final CharSequence source, final int sourceOffset,
                final int sourceLength, final char[] output, final int outputOffset, final int producedLength) {
            writeSequence(source, sourceOffset, sourceLength, output, outputOffset, producedLength);
        }

        @Override
        protected void applyArrayToOutput(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            writeArray(source, sourceOffset, sourceLength, output, outputOffset, producedLength);
        }

        private void writeSequence(final CharSequence source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            if (!writeForwardSequence(this.opcodes, this.operands, source, sourceOffset, sourceLength, output,
                    outputOffset, producedLength)) {
                copySource(source, sourceOffset, sourceLength, output, outputOffset);
            }
        }

        private void writeArray(final char[] source, final int sourceOffset, final int sourceLength,
                final char[] output, final int outputOffset, final int producedLength) {
            if (!writeForwardArray(this.opcodes, this.operands, source, sourceOffset, sourceLength, output,
                    outputOffset, producedLength)) {
                System.arraycopy(source, sourceOffset, output, outputOffset, sourceLength);
            }
        }
    }

    private static boolean writeForwardSequence(final char[] opcodes, final int[] operands,
            final CharSequence source, final int sourceOffset, final int sourceLength, final char[] output,
            final int outputOffset, final int producedLength) {
        int currentLength = sourceLength;
        int position = 0;
        int sourceIndex = 0;
        int outputIndex = 0;
        for (int index = 0; index < opcodes.length; index++) {
            final char opcode = opcodes[index];
            final int operand = operands[index];
            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = Math.min(operand, sourceLength - sourceIndex);
                    copySource(source, sourceOffset + sourceIndex, skipCount, output, outputOffset + outputIndex);
                    sourceIndex += skipCount;
                    outputIndex += skipCount;
                    position = position + operand - 1;
                    break;
                case DELETE_OPCODE:
                    if (position < 0 || position > currentLength) {
                        return false;
                    }
                    final int deletedLength = Math.min(operand, currentLength - position);
                    if (sourceIndex + deletedLength > sourceLength) {
                        return false;
                    }
                    sourceIndex += deletedLength;
                    currentLength -= deletedLength;
                    position--;
                    break;
                case INSERT_OPCODE:
                    if (position < 0 || position > currentLength || outputIndex >= producedLength) {
                        return false;
                    }
                    output[outputOffset + outputIndex] = (char) operand;
                    outputIndex++;
                    currentLength++;
                    break;
                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength || sourceIndex >= sourceLength
                            || outputIndex >= producedLength) {
                        return false;
                    }
                    sourceIndex++;
                    output[outputOffset + outputIndex] = (char) operand;
                    outputIndex++;
                    break;
                default:
                    throw new AssertionError(MSG_OPCODE + opcode);
            }
            position++;
        }
        final int remainingLength = sourceLength - sourceIndex;
        if (remainingLength > producedLength - outputIndex) {
            return false;
        }
        copySource(source, sourceOffset + sourceIndex, remainingLength, output, outputOffset + outputIndex);
        return outputIndex + remainingLength == producedLength;
    }

    private static boolean writeForwardArray(final char[] opcodes, final int[] operands, final char[] source,
            final int sourceOffset, final int sourceLength, final char[] output, final int outputOffset,
            final int producedLength) {
        int currentLength = sourceLength;
        int position = 0;
        int sourceIndex = 0;
        int outputIndex = 0;
        for (int index = 0; index < opcodes.length; index++) {
            final char opcode = opcodes[index];
            final int operand = operands[index];
            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = Math.min(operand, sourceLength - sourceIndex);
                    System.arraycopy(source, sourceOffset + sourceIndex, output, outputOffset + outputIndex,
                            skipCount);
                    sourceIndex += skipCount;
                    outputIndex += skipCount;
                    position = position + operand - 1;
                    break;
                case DELETE_OPCODE:
                    if (position < 0 || position > currentLength) {
                        return false;
                    }
                    final int deletedLength = Math.min(operand, currentLength - position);
                    if (sourceIndex + deletedLength > sourceLength) {
                        return false;
                    }
                    sourceIndex += deletedLength;
                    currentLength -= deletedLength;
                    position--;
                    break;
                case INSERT_OPCODE:
                    if (position < 0 || position > currentLength || outputIndex >= producedLength) {
                        return false;
                    }
                    output[outputOffset + outputIndex] = (char) operand;
                    outputIndex++;
                    currentLength++;
                    break;
                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength || sourceIndex >= sourceLength
                            || outputIndex >= producedLength) {
                        return false;
                    }
                    sourceIndex++;
                    output[outputOffset + outputIndex] = (char) operand;
                    outputIndex++;
                    break;
                default:
                    throw new AssertionError(MSG_OPCODE + opcode);
            }
            position++;
        }
        final int remainingLength = sourceLength - sourceIndex;
        if (remainingLength > producedLength - outputIndex) {
            return false;
        }
        System.arraycopy(source, sourceOffset + sourceIndex, output, outputOffset + outputIndex, remainingLength);
        return outputIndex + remainingLength == producedLength;
    }

    private static boolean writeBackwardSequence(final char[] opcodes, final int[] operands,
            final CharSequence source, final int sourceOffset, final int sourceLength, final char[] output,
            final int outputOffset, final int producedLength) {
        int currentLength = sourceLength;
        int position = sourceLength - 1;
        int sourceEnd = sourceLength;
        int outputEnd = producedLength;
        for (int index = 0; index < opcodes.length; index++) {
            final char opcode = opcodes[index];
            final int operand = operands[index];
            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = Math.min(operand, sourceEnd);
                    sourceEnd -= skipCount;
                    outputEnd -= skipCount;
                    if (outputEnd < 0) {
                        return false;
                    }
                    copySource(source, sourceOffset + sourceEnd, skipCount, output, outputOffset + outputEnd);
                    position = position - operand + 1;
                    break;
                case DELETE_OPCODE:
                    final int deleteEndExclusive = position + 1;
                    position -= operand - 1;
                    if (position < 0 || position > currentLength || position > deleteEndExclusive) {
                        return false;
                    }
                    final int deletedLength = Math.min(deleteEndExclusive, currentLength) - position;
                    if (sourceEnd < deletedLength) {
                        return false;
                    }
                    sourceEnd -= deletedLength;
                    currentLength -= deletedLength;
                    break;
                case INSERT_OPCODE:
                    if (position < -1 || position >= currentLength || outputEnd <= 0) {
                        return false;
                    }
                    outputEnd--;
                    output[outputOffset + outputEnd] = (char) operand;
                    currentLength++;
                    position++;
                    break;
                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength || sourceEnd <= 0 || outputEnd <= 0) {
                        return false;
                    }
                    sourceEnd--;
                    outputEnd--;
                    output[outputOffset + outputEnd] = (char) operand;
                    break;
                default:
                    throw new AssertionError(MSG_OPCODE + opcode);
            }
            position--;
        }
        if (sourceEnd != outputEnd) {
            return false;
        }
        copySource(source, sourceOffset, sourceEnd, output, outputOffset);
        return true;
    }

    private static boolean writeBackwardArray(final char[] opcodes, final int[] operands, final char[] source,
            final int sourceOffset, final int sourceLength, final char[] output, final int outputOffset,
            final int producedLength) {
        int currentLength = sourceLength;
        int position = sourceLength - 1;
        int sourceEnd = sourceLength;
        int outputEnd = producedLength;
        for (int index = 0; index < opcodes.length; index++) {
            final char opcode = opcodes[index];
            final int operand = operands[index];
            switch (opcode) {
                case SKIP_OPCODE:
                    final int skipCount = Math.min(operand, sourceEnd);
                    sourceEnd -= skipCount;
                    outputEnd -= skipCount;
                    if (outputEnd < 0) {
                        return false;
                    }
                    System.arraycopy(source, sourceOffset + sourceEnd, output, outputOffset + outputEnd, skipCount);
                    position = position - operand + 1;
                    break;
                case DELETE_OPCODE:
                    final int deleteEndExclusive = position + 1;
                    position -= operand - 1;
                    if (position < 0 || position > currentLength || position > deleteEndExclusive) {
                        return false;
                    }
                    final int deletedLength = Math.min(deleteEndExclusive, currentLength) - position;
                    if (sourceEnd < deletedLength) {
                        return false;
                    }
                    sourceEnd -= deletedLength;
                    currentLength -= deletedLength;
                    break;
                case INSERT_OPCODE:
                    if (position < -1 || position >= currentLength || outputEnd <= 0) {
                        return false;
                    }
                    outputEnd--;
                    output[outputOffset + outputEnd] = (char) operand;
                    currentLength++;
                    position++;
                    break;
                case REPLACE_OPCODE:
                    if (position < 0 || position >= currentLength || sourceEnd <= 0 || outputEnd <= 0) {
                        return false;
                    }
                    sourceEnd--;
                    outputEnd--;
                    output[outputOffset + outputEnd] = (char) operand;
                    break;
                default:
                    throw new AssertionError(MSG_OPCODE + opcode);
            }
            position--;
        }
        if (sourceEnd != outputEnd) {
            return false;
        }
        System.arraycopy(source, sourceOffset, output, outputOffset, sourceEnd);
        return true;
    }
}
