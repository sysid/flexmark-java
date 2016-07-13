package com.vladsch.flexmark.ext.spec.example.internal;

import com.vladsch.flexmark.ext.spec.example.*;
import com.vladsch.flexmark.internal.*;
import com.vladsch.flexmark.internal.util.options.DataHolder;
import com.vladsch.flexmark.internal.util.sequence.BasedSequence;
import com.vladsch.flexmark.internal.util.sequence.BasedSequenceImpl;
import com.vladsch.flexmark.internal.util.sequence.SubSequence;
import com.vladsch.flexmark.node.Block;
import com.vladsch.flexmark.node.Node;
import com.vladsch.flexmark.parser.block.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vladsch.flexmark.spec.SpecReader.EXAMPLE_KEYWORD;
import static com.vladsch.flexmark.spec.SpecReader.OPTIONS_KEYWORD;

public class SpecExampleBlockParser extends AbstractBlockParser {
    private static final Pattern OPTIONS_PATTERN = Pattern.compile("^\\s*(\\()?([^:]*)(?:(:)(\\S+)\\s*?)?(\\))?(?:\\s+(options)\\s*(\\()?([^)]+)(\\))?)?\\s*$".replace("options", OPTIONS_KEYWORD));
    private static final int GROUP_COORD_OPEN = 1;
    private static final int GROUP_SECTION = 2;
    private static final int GROUP_NUMBER_SEPARATOR = 3;
    private static final int GROUP_NUMBER = 4;
    private static final int GROUP_COORD_CLOSE = 5;
    private static final int GROUP_OPTION_KEYWORD = 6;
    private static final int GROUP_OPTIONS_OPEN = 7;
    private static final int GROUP_OPTIONS = 8;
    private static final int GROUP_OPTIONS_CLOSE = 9;

    private final SpecExampleBlock block = new SpecExampleBlock();
    private BlockContent content = new BlockContent();
    final private SpecExampleOptions myOptions;

    public SpecExampleBlockParser(DataHolder options) {
        myOptions = new SpecExampleOptions(options);
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        BasedSequence line = state.getLine();
        if (line.startsWith(myOptions.exampleBreak)) {
            block.setClosingMarker(line.subSequence(0, myOptions.exampleBreak.length()));
            return BlockContinue.finished();
        }
        return BlockContinue.atIndex(0);
    }

    @Override
    public void addLine(ParserState state, BasedSequence line) {
        content.add(line, state.getIndent());
    }

    @Override
    public boolean isPropagatingLastBlankLine(BlockParser lastMatchedBlockParser) {
        return false;
    }

    @Override
    public void closeBlock(ParserState parserState) {
        // first line, if not blank, has the info string
        List<BasedSequence> lines = content.getLines();
        if (lines.size() > 0) {
            BasedSequence info = lines.get(0);

            int exampleKeyWordStart = myOptions.exampleBreak.length() + 1;
            int exampleKeyWordEnd = exampleKeyWordStart + EXAMPLE_KEYWORD.length();
            BasedSequence exampleKeyword = info.subSequence(exampleKeyWordStart, exampleKeyWordEnd);
            BasedSequence optionsChars = info.subSequence(exampleKeyWordEnd);
            Matcher options = OPTIONS_PATTERN.matcher(optionsChars.toString().replace('\u00A0', ' '));

            block.setOpeningMarker(info.subSequence(0, myOptions.exampleBreak.length()));
            block.setExampleKeyword(exampleKeyword);

            if (options.matches()) {
                // @formatter:off
                if (options.group(GROUP_COORD_OPEN) != null && !options.group(GROUP_COORD_OPEN).trim().isEmpty()) block.setCoordOpeningMarker(optionsChars.subSequence(options.start(GROUP_COORD_OPEN), options.end(GROUP_COORD_OPEN)).trim());
                if (options.group(GROUP_SECTION) != null && !options.group(GROUP_SECTION).trim().isEmpty()) block.setSection(optionsChars.subSequence(options.start(GROUP_SECTION), options.end(GROUP_SECTION)).trim());
                if (options.group(GROUP_NUMBER_SEPARATOR) != null && !options.group(GROUP_NUMBER_SEPARATOR).trim().isEmpty()) block.setNumberSeparator(optionsChars.subSequence(options.start(GROUP_NUMBER_SEPARATOR), options.end(GROUP_NUMBER_SEPARATOR)).trim());
                if (options.group(GROUP_NUMBER) != null && !options.group(GROUP_NUMBER).trim().isEmpty()) block.setNumber(optionsChars.subSequence(options.start(GROUP_NUMBER), options.end(GROUP_NUMBER)).trim());
                if (options.group(GROUP_COORD_CLOSE) != null && !options.group(GROUP_COORD_CLOSE).trim().isEmpty()) block.setCoordClosingMarker(optionsChars.subSequence(options.start(GROUP_COORD_CLOSE), options.end(GROUP_COORD_CLOSE)).trim());
                if (options.group(GROUP_OPTION_KEYWORD) != null && !options.group(GROUP_OPTION_KEYWORD).trim().isEmpty()) block.setOptionsKeyword(optionsChars.subSequence(options.start(GROUP_OPTION_KEYWORD), options.end(GROUP_OPTION_KEYWORD)).trim());
                if (options.group(GROUP_OPTIONS_OPEN) != null && !options.group(GROUP_OPTIONS_OPEN).trim().isEmpty()) block.setOptionsOpeningMarker(optionsChars.subSequence(options.start(GROUP_OPTIONS_OPEN), options.end(GROUP_OPTIONS_OPEN)).trim());
                if (options.group(GROUP_OPTIONS) != null && !options.group(GROUP_OPTIONS).trim().isEmpty()) block.setOptions(optionsChars.subSequence(options.start(GROUP_OPTIONS), options.end(GROUP_OPTIONS)).trim());
                if (options.group(GROUP_OPTIONS_CLOSE) != null && !options.group(GROUP_OPTIONS_CLOSE).trim().isEmpty()) block.setOptionsClosingMarker(optionsChars.subSequence(options.start(GROUP_OPTIONS_CLOSE), options.end(GROUP_OPTIONS_CLOSE)).trim());
                // @formatter:off
            }

            // if we create option nodes, we break up the options
            if (block.getOptionsKeyword().isNotNull()) {

            }

            BasedSequence chars = content.getSpanningChars();
            BasedSequence spanningChars = chars.baseSubSequence(chars.getStartOffset(), lines.get(0).getEndOffset());

            if (lines.size() > 1) {
                // have more lines
                block.setContent(spanningChars, lines.subList(1, lines.size()));

                // need to find the parts
                boolean inSource = true;
                boolean inHtml = false;
                boolean inAst = false;
                int sectionStart = -1;
                BasedSequence prevLine = SubSequence.NULL;
                BasedSequence lastLine = lines.get(lines.size() - 1);
                String typeBreak = myOptions.typeBreak;
                int typeBreakLength = typeBreak.length();

                for (BasedSequence line : lines.subList(1, lines.size())) {
                    if (line.length() == typeBreakLength + line.countTrailing(BasedSequenceImpl.EOL_CHARS) && line.matchChars(typeBreak)) {
                        if (inSource) {
                            inSource = false;
                            if (sectionStart != -1) {
                                block.setSource(line.baseSubSequence(sectionStart, line.getStartOffset() - prevLine.countTrailing(BasedSequenceImpl.EOL_CHARS)));
                            }
                            block.setHtmlSeparator(line.subSequence(0, typeBreakLength));
                            inHtml = true;
                            sectionStart = -1;
                        } else if (inHtml) {
                            inHtml = false;
                            if (sectionStart != -1) {
                                block.setHtml(line.baseSubSequence(sectionStart, line.getStartOffset() - prevLine.countTrailing(BasedSequenceImpl.EOL_CHARS)));
                            }
                            block.setAstSeparator(line.subSequence(0, typeBreakLength));
                            inAst = true;
                            sectionStart = -1;
                        } else {
                            if (sectionStart == -1) {
                                sectionStart = line.getStartOffset();
                            }
                        }
                    } else {
                        if (sectionStart == -1) {
                            sectionStart = line.getStartOffset();
                        }
                    }

                    prevLine = line;

                    if (line == lastLine) {
                        // done
                        if (inSource) {
                            if (sectionStart != -1) {
                                block.setSource(line.baseSubSequence(sectionStart, line.getEndOffset() - prevLine.countTrailing(BasedSequenceImpl.EOL_CHARS)));
                            }
                        } else if (inHtml) {
                            if (sectionStart != -1) {
                                block.setHtml(line.baseSubSequence(sectionStart, line.getEndOffset() - prevLine.countTrailing(BasedSequenceImpl.EOL_CHARS)));
                            }
                        } else if (inAst) {
                            if (sectionStart != -1) {
                                block.setAst(line.baseSubSequence(sectionStart, line.getEndOffset() - prevLine.countTrailing(BasedSequenceImpl.EOL_CHARS)));
                            }
                        }

                        break;
                    }
                }

                // here if we create section nodes
                if (block.getSource().isNotNull()) {
                    Node node = new SpecExampleSource(block.getSource());
                    block.appendChild(node);
                }

                if (block.getHtmlSeparator().isNotNull()) {
                    Node node = new SpecExampleSeparator(block.getHtmlSeparator());
                    block.appendChild(node);

                    if (block.getHtml().isNotNull()) {
                        node = new SpecExampleHtml(block.getHtml());
                        block.appendChild(node);
                    }

                    if (block.getAstSeparator().isNotNull()) {
                        node = new SpecExampleSeparator(block.getAstSeparator());
                        block.appendChild(node);
                        if (block.getAst().isNotNull()) {
                            node = new SpecExampleAst(block.getAst());
                            block.appendChild(node);
                        }
                    }
                }
            } else {
                block.setContent(spanningChars, SubSequence.EMPTY_LIST);
            }
        } else {
            block.setContent(content);
        }

        block.setCharsFromContent();
        content = null;
    }

    public static class Factory implements CustomBlockParserFactory {
        @Override
        public Set<Class<? extends CustomBlockParserFactory>> getAfterDependents() {
            return new HashSet<>(Arrays.asList(
                    BlockQuoteParser.Factory.class,
                    HeadingParser.Factory.class
                    //FencedCodeBlockParser.Factory.class
                    //HtmlBlockParser.Factory.class,
                    //ThematicBreakParser.Factory.class,
                    //ListBlockParser.Factory.class,
                    //IndentedCodeBlockParser.Factory.class
            ));
        }

        @Override
        public Set<Class<? extends CustomBlockParserFactory>> getBeforeDependents() {
            return new HashSet<>(Arrays.asList(
                    //BlockQuoteParser.Factory.class,
                    //HeadingParser.Factory.class,
                    FencedCodeBlockParser.Factory.class,
                    HtmlBlockParser.Factory.class,
                    ThematicBreakParser.Factory.class,
                    ListBlockParser.Factory.class,
                    IndentedCodeBlockParser.Factory.class
            ));
        }

        @Override
        public boolean affectsGlobalScope() {
            return false;
        }

        @Override
        public BlockParserFactory create(DataHolder options) {
            return new SpecExampleBlockParser.BlockFactory(options);
        }
    }

    private static class BlockFactory extends AbstractBlockParserFactory {
        final private SpecExampleOptions myOptions;

        private BlockFactory(DataHolder options) {
            super(options);
            myOptions = new SpecExampleOptions(options);
        }

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            int nextNonSpace = state.getNextNonSpaceIndex();
            BasedSequence line = state.getLine();
            if (state.getIndex() == 0) {
                int breakLength = myOptions.exampleBreak.length();
                if (line.length() >= breakLength + 1 + EXAMPLE_KEYWORD.length() && line.startsWith(myOptions.exampleBreak) && line.matchChars(EXAMPLE_KEYWORD, breakLength + 1) && " \t\u00A0".contains(String.valueOf(line.charAt(breakLength)))) {
                    SpecExampleBlockParser blockParser = new SpecExampleBlockParser(state.getProperties());
                    blockParser.block.setOpeningMarker(line.subSequence(0, breakLength));
                    //blockParser.addLine(state, state.getLineWithEOL());
                    return BlockStart.of(blockParser).atIndex(-1);
                }
            }
            return BlockStart.none();
        }
    }
}
