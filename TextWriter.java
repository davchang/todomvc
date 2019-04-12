package com.sst.storefront.psg.writers;

import com.google.common.collect.Lists;
import com.sst.storefront.psg.models.HAlign;
import com.sst.storefront.psg.models.PageAnchor;
import com.sst.storefront.psg.models.PageCount;
import com.sst.storefront.psg.models.PageData;
import com.sst.storefront.psg.models.PageNumberData;
import com.sst.storefront.psg.models.PageNumberLink;
import com.sst.storefront.psg.models.PageNumberLinkData;
import com.sst.storefront.psg.models.Pagination;
import com.sst.storefront.psg.models.Text;
import com.sst.storefront.psg.models.TextType;
import com.sst.storefront.psg.util.ExpressionLangInterpreter;
import com.sst.storefront.psg.util.IterationUtil;
import com.sst.storefront.psg.util.TextExtractorHelper;
import com.sst.storefront.psg.util.TextWriterUtils;
import com.sst.storefront.psg.writers.graphics.Layout;
import com.sst.storefront.psg.writers.graphics.Point;
import com.sst.storefront.util.WriterUtil;
import com.sst.storefront.psg.util.FontSingleton;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.io.IOException;

import static com.sst.storefront.psg.models.TextType.BOLD;
import static com.sst.storefront.psg.models.TextType.ICON;
import static com.sst.storefront.psg.models.TextType.NORMAL;
import static com.sst.storefront.psg.models.TextType.SUBSCRIPT;
import static com.sst.storefront.psg.models.TextType.SUPERSCRIPT;
import static java.lang.String.format;

/**
 * @author Dario Flores (dflores@tacitknowledge.com)
 */
public class TextWriter extends AbstractTextWriter {

    private static String SUB_START = "#sub#";

    private static String SUB_END = "#/sub#";

    private static String SUP_START = "#sup#";

    private static String SUP_END = "#/sup#";

    private static String STRONG_START = "#strong#";

    private static String STRONG_END = "#/strong#";

    private static String ICON_START = "#i#";

    private static String ICON_END = "#/i#";

    private static String[] INTERNAL_END_TAGS = {SUB_END, SUP_END, STRONG_END, ICON_END};

    private static String[] INTERNAL_START_TAGS = {SUB_START, SUP_START, STRONG_START, ICON_START};

    private static final String EMPTY_TAG_REGEX="#\\w+#\\s*#\\/\\w+#";

    static {
        FONT_CACHE.put(DEFAULT_FONT, PDType1Font.HELVETICA_BOLD);
    }

    public float write(final Text text, final Layout layout) {
        final float width = layout.getWidth();

        final TextExtractorHelper helper = new TextExtractorHelper();
        final TextExtractorHelper.TextExtractorResponse response = helper.extractTextFromComponent(text);

        final PDFont font = getFont(text.getExternalFontURI(), text.getFontId(), text.getJobId(), text.getPdDocument());
        final PDFont boldFont = getFont(text.getExternalBoldFontURI(), text.getBoldFontId(), text.getJobId(),
            text.getPdDocument());
        // final PDFont iconFont = getFont("psg/fonts/sst-wcm-2015.ttf", "sst-wcm-2015", text.getJobId(), text.getPdDocument());
        final PDFont iconFont = FontSingleton.getInstance().getFont("psg/fonts/sst-wcm-2015.ttf", "sst-wcm-2015", text.getPdDocument());

        final List<String> lines = splitToLinesWithNewLine(response.getText(), font, text.getFontSize(), width);
        final float heightSpacing = calculateContentHeight(font, text.getFontSize()) + text.getLineSpacing();
        final Point textOrigin = layout.getNextLineOrigin();

        LOGGER.info("write 1");
        if (BooleanUtils.isTrue(text.isPagination())) {
            final Pagination pagination = (Pagination) text.getSection().get(0);
            final PageData pageData = new PageData();
            LOGGER.info("write 2");
            pageData.setOrigin(new Point(textOrigin.getX(), textOrigin.getY()));
            pageData.setPdPage(text.getPdPage());
            pageData.setWidth(width);
            pagination.addPageData(pageData);
            return 0f;
        } else if (BooleanUtils.isTrue(text.isPageCount())) {
            final PageCount pageCount = (PageCount) text.getSection().get(0);
            LOGGER.info("write 3");
            pageCount.setOrigin(new Point(textOrigin.getX(), textOrigin.getY()));
            pageCount.setPdDocument(text.getPdDocument());
            pageCount.setPdPage(text.getPdPage());
            pageCount.setWidth(width);
            return 0f;
        } else {
            if (WriterUtil.containsPageAnchor(text)) {
                LOGGER.info("write 4");
                final PageAnchor pageAnchor = (PageAnchor) WriterUtil.getPageAnchor(text);
                if (IterationUtil.validCondition(pageAnchor.getCondition(), pageAnchor.getVar(), text.getData())) {
                    final PageNumberData pageNumberData = new PageNumberData();
                    pageNumberData.setPdPage(text.getPdPage());
                    pageNumberData
                        .setTextValue(
                            ExpressionLangInterpreter.parseExpression(pageAnchor.getValue(), text.getData()));
                    pageAnchor.addPageNumberData(pageNumberData);
                }
            } else if (WriterUtil.containsPageNumberLink(text)) {
                LOGGER.info("write 5");
                final PageNumberLink pageNumberLink = (PageNumberLink) WriterUtil.getPageNumberLinkSection(text);
                LOGGER.info(format("Page Number Link Value = ", pageNumberLink.getValue()));
                final PageNumberLinkData pageNumberLinkData = createPageNumberLinkData(
                    new Point(textOrigin.getX(), textOrigin.getY()), text.getPdPage(), width,
                    ExpressionLangInterpreter.parseExpression(pageNumberLink.getValue(), text.getData()),
                    pageNumberLink.getValue());
                pageNumberLink.addPageNumberLinkData(pageNumberLinkData);
                if (pageNumberLink.isDocumentLink()) {
                    pageNumberLinkData.setMessage(ExpressionLangInterpreter
                        .parseExpression(text.getSection().get(0).getValue(), text.getData()));
                }
            }

            LOGGER.info("write 9 : calling writeLinesToStream...");
            return writeLinesToStream(lines, font, boldFont, iconFont, text.getFontSize(), textOrigin.getX(), textOrigin.getY(),
                heightSpacing, text.getLineSpacing(), width, text.getHorizontalAlignment(),
                response, text.getContentStream());
        }
    }

    private PageNumberLinkData createPageNumberLinkData(final Point origin, final PDPage pdPage, final float width,
                                                        final String textValue, final String referenceValue) {
        final PageNumberLinkData pageNumberLinkData = new PageNumberLinkData();
        pageNumberLinkData.setOrigin(origin);
        pageNumberLinkData.setPdPage(pdPage);
        pageNumberLinkData.setWidth(width);
        pageNumberLinkData.setTextValue(textValue);
        pageNumberLinkData.setReferenceValue(referenceValue);
        return pageNumberLinkData;
    }

    private float writeLinesToStream(final List<String> lines, final PDFont font, final PDFont boldFont, final PDFont iconFont,
                                     final float fontSize, final float tx,
                                     final float ty, final float heightSpacing, final float lineSpacing, final float width,
                                     final HAlign horizontalAlignment, final TextExtractorHelper.TextExtractorResponse response,
                                     final PDPageContentStream contentStream) {
        LOGGER.info("writeLinesToStream...");
        final int[] fromInclusiveIndex = {0};
        final int[] toExclusiveIndex = {0};

        final float[] x_shift = {0f};
        final float[] y_lastPosition = {0f};

        IntStream.range(0, lines.size())
            .forEach(idx -> {

                toExclusiveIndex[0] = fromInclusiveIndex[0] + lines.get(idx).length();

                x_shift[0] = getXAlignmentShift(lines.get(idx), font, fontSize, width,
                    horizontalAlignment);

                y_lastPosition[0] = ty - (heightSpacing * (idx + 1) - lineSpacing);
                writeLineToStream(lines.get(idx), font, boldFont, iconFont, fontSize, tx + x_shift[0], y_lastPosition[0],
                    fromInclusiveIndex[0], toExclusiveIndex[0], response,
                    contentStream);

                fromInclusiveIndex[0] = toExclusiveIndex[0];

            });

        return y_lastPosition[0];
    }

    private void writeLineToStream(final String line, final PDFont font, final PDFont boldFont, final PDFont iconFont, final float fontSize,
                                   final float ttx, final float tty, final int fromInclusiveIndex, final int toExclusiveIndex,
                                   final TextExtractorHelper.TextExtractorResponse response, final PDPageContentStream contentStream) {
        LOGGER.info("writeLineToStream...");
        if (isComposedText(line)) {
            LOGGER.info("writeLineToStream...1");
            writeComposedLineToStream(line, font, boldFont, iconFont, fontSize, ttx, tty, contentStream);

        } else {
            LOGGER.info("writeLineToStream...2");
            writeSimpleLineToStream(line, font, fontSize, ttx, tty, fromInclusiveIndex, toExclusiveIndex, response,
                contentStream);
        }

    }

    private void writeComposedLineToStream(final String line, final PDFont font, final PDFont boldFont, final PDFont iconFont,
                                           final float fontSize, final float ttx,
                                           final float tty, final PDPageContentStream contentStream) {
       LOGGER.info("writeComposedLineToStream...");
        final List<String[]> lineGroups = groupTextLine(line);
        final float[] x_shift = {ttx};

        lineGroups.stream().forEach(group -> {

            if (group[1].equals(NORMAL.value())) {

                x_shift[0] += writePartialLine(group[0], font, fontSize, x_shift[0], tty, contentStream, null);

            } else if (group[1].equals(BOLD.value())) {

                x_shift[0] += writePartialLine(group[0], boldFont, fontSize, x_shift[0], tty, contentStream, null);

            } else if (group[1].equals(SUBSCRIPT.value())) {

                x_shift[0] += writePartialLine(group[0], font, fontSize / 2.4f, x_shift[0], tty, contentStream, null);

            } else if (group[1].equals(SUPERSCRIPT.value())) {

                x_shift[0] += writePartialLine(group[0], font, fontSize / 2.2f, x_shift[0],
                    tty + (this.calculateContentHeight(font, fontSize) / 2.0f), contentStream, null);

            } else if (group[1].equals(ICON.value())) {

                x_shift[0] += writePartialLine(group[0], iconFont, fontSize + 2, x_shift[0], tty, contentStream, new Color(230, 99, 58));
            }

        });

    }

    private void writeSimpleLineToStream(final String line, final PDFont font, final float fontSize, final float ttx,
                                         final float tty, final int fromInclusiveIndex, final int toExclusiveIndex,
                                         final TextExtractorHelper.TextExtractorResponse response, final PDPageContentStream contentStream) {
        LOGGER.info("Calculating text to write in line...");
        final List<int[]> superScriptIntervals = getIntervalsWithinRange(fromInclusiveIndex, toExclusiveIndex,
            response.getSuperScriptRanges());

        final List<int[]> subScriptIntervals = getIntervalsWithinRange(fromInclusiveIndex, toExclusiveIndex,
            response.getSubScriptRanges());

        final List<int[]> subSuperIntervals = Lists.newArrayList();
        subSuperIntervals.addAll(superScriptIntervals);
        subSuperIntervals.addAll(subScriptIntervals);

        final List<int[]> complementIntervals = getComplementIntervals(fromInclusiveIndex, toExclusiveIndex,
            subSuperIntervals);

        final List<int[]> allIntervals = mergeIntervals(subSuperIntervals, complementIntervals);

        final float[] x_shift = {ttx};

        allIntervals.stream().forEach(interval -> {
            final String partialText = line
                .substring(interval[0] - fromInclusiveIndex, interval[1] - fromInclusiveIndex);

            LOGGER.info("partialText..." + partialText);

            if (superScriptIntervals.contains(interval)) {
                x_shift[0] += writePartialLine(partialText, font, fontSize / 2.2f, x_shift[0],
                    tty + (this.calculateContentHeight(font, fontSize) / 2.0f), contentStream, null);
            } else if (subScriptIntervals.contains(interval)) {
                x_shift[0] += writePartialLine(partialText, font, fontSize / 2.4f, x_shift[0], tty, contentStream, null);
            } else {
                x_shift[0] += writePartialLine(partialText, font, fontSize, x_shift[0], tty, contentStream, null);
            }
        });
    }

    private boolean isComposedText(final String txt) {

        return StringUtils.containsAny(txt, INTERNAL_END_TAGS);
    }

    protected List<String[]> groupTextLine(final String text) {

        int endSup = text.indexOf(SUP_END);
        int endSub = text.indexOf(SUB_END);
        int endStrong = text.indexOf(STRONG_END);
        int endIcon = text.indexOf(ICON_END);

        final List<String[]> groups = new ArrayList();

        String rest = text;

        while (endSup >= 0 || endSub >= 0 || endStrong >= 0 || endIcon >= 0) {

            int endMin = getMin(endSup, endSub, endStrong, endIcon);
            String startTag = getStartTag(endMin, endSup, endSub, endStrong, endIcon);
            String endTag = getEndTag(endMin, endSup, endSub, endStrong, endIcon);
            int startMin = (rest.indexOf(startTag) >= 0) ? rest.indexOf(startTag) : 0;
            final TextType textTypeForTag = getTextTypeForTag(startTag);

            if (startMin > 0) {
                groups.add(new String[]{rest.substring(0, startMin), NORMAL.value()});
            }

            try{

                groups.add(new String[]{rest.substring(startMin + startTag.length(), endMin), textTypeForTag.value()});

            }catch (Exception ex){
                LOGGER.error(format("Error in line [%s] (%d,%d)",rest,startMin, startTag.length()));
                throw ex;
            }

            rest = rest.substring(endMin + endTag.length(), rest.length());

            endSup = rest.indexOf(SUP_END);
            endSub = rest.indexOf(SUB_END);
            endStrong = rest.indexOf(STRONG_END);
            endIcon = rest.indexOf(ICON_END);

        }

        if (StringUtils.isNotEmpty(rest)) {
            groups.add(new String[]{rest, NORMAL.value()});
        }

        return groups;
    }

    private TextType getTextTypeForTag(final String tag) {

        if (tag.equals(SUP_START)) {
            return TextType.SUPERSCRIPT;
        } else if (tag.equals(SUB_START)) {
            return TextType.SUBSCRIPT;
        } else if (tag.equals(STRONG_START)) {
            return TextType.BOLD;
        } else if (tag.equals(ICON_START)) {
            return TextType.ICON;
        }

        return TextType.NORMAL;
    }

    private String getStartTag(final int min, final int endSup, final int endSub, final int endStrong, final int endIcon) {

        if (min == endSup) {
            return SUP_START;
        } else if (min == endSub) {
            return SUB_START;
        } else if (min == endIcon) {
            return ICON_START;
        } else {
            return STRONG_START;
        }

    }

    private String getEndTag(final int min, final int endSup, final int endSub, final int endStrong, final int endIcon) {

        if (min == endSup) {
            return SUP_END;
        } else if (min == endSub) {
            return SUB_END;
        } else if (min == endIcon) {
            return ICON_END;
        } else {
            return STRONG_END;
        }

    }

    private int getMin(int... nums) {

        if (nums.length > 1) {

            final int[] min = {Arrays.stream(nums, 0, nums.length)
                .filter(xx -> xx >= 0).findFirst().orElse(0)};

            Arrays.stream(nums, 0, nums.length)
                .filter(xx -> xx >= 0)
                .forEach(xx -> {
                    min[0] = (min[0] > xx) ? xx : min[0];
                });

            return min[0];
        } else {
            return nums[0];
        }

    }

    private List<int[]> mergeIntervals(List<int[]>... elements) {

        final List<int[]> allIntervals = Lists.newArrayList();

        Arrays.stream(elements).forEach(element -> {

            allIntervals.addAll(element);

        });

        return allIntervals.stream().sorted((o1, o2) -> o1[0] <= o2[0] ? -1 : 1).collect(Collectors.toList());
    }

    private List<int[]> getComplementIntervals(final int fromInclusiveIndex, final int toExclusiveIndex,
                                               final List<int[]> intervals) {

        final List<int[]> complement = Lists.newArrayList();

        int[] aftInterval = null;

        for (int[] currentInterval : intervals) {

            if (aftInterval == null) {
                complement.add(new int[]{fromInclusiveIndex, currentInterval[0]});
            } else {
                complement.add(new int[]{aftInterval[1], currentInterval[0]});
            }

            aftInterval = currentInterval;
        }

        if (aftInterval != null) {

            complement.add(new int[]{aftInterval[1], toExclusiveIndex});

        } else {
            complement.add(new int[]{fromInclusiveIndex, toExclusiveIndex});
        }

        return complement;
    }

    private List<int[]> getIntervalsWithinRange(final int fromInclusiveIndex, final int toExclusiveIndex,
                                                final List<int[]> intervalRanges) {

        final List<int[]> ranges = Lists.newArrayList();

        intervalRanges.stream().forEach(range -> {

            if (range[0] <= fromInclusiveIndex && fromInclusiveIndex <= range[1] && range[1] <= toExclusiveIndex) {

                ranges.add(new int[]{fromInclusiveIndex, range[1]});

            } else if (fromInclusiveIndex <= range[0] && range[1] <= toExclusiveIndex) {

                ranges.add(new int[]{range[0], range[1]});

            } else if (fromInclusiveIndex <= range[0] && range[1] <= toExclusiveIndex && toExclusiveIndex <= range[1]) {

                ranges.add(new int[]{range[0], toExclusiveIndex});

            }

        });

        return ranges.stream().sorted((o1, o2) -> o1[0] <= o2[0] ? -1 : 1).collect(Collectors.toList());

    }

    private List<String> splitToLinesWithNewLine(final String text, final PDFont font, final float fontSize,
                                                 final float width) {

        final String[] splitText = text.split("\\r?\\n");
        final List<String> paragraph = Lists.newArrayList();

        IntStream.range(0, splitText.length).forEach(idx -> {

            final List<String> line = TextWriterUtils.splitToLines(splitText[idx], font, fontSize, width);
            paragraph.addAll(line);
        });

        return completeSpecialTags(paragraph);
    }

    private List<String> completeSpecialTags(final List<String> paragraph){

        final List<String> completedParagraph= Lists.newArrayList();

        for(String line:paragraph){

            if(StringUtils.containsAny(line,INTERNAL_START_TAGS) && !StringUtils.containsAny(line,INTERNAL_END_TAGS) ){

                for(int idx=0;idx<INTERNAL_START_TAGS.length;idx++){

                    if(line.indexOf(INTERNAL_START_TAGS[idx])>=0){
                        completedParagraph.add((line+INTERNAL_END_TAGS[idx])
                            .replaceAll(EMPTY_TAG_REGEX,StringUtils.EMPTY));
                    }

                }

            }else if(StringUtils.containsAny(line,INTERNAL_END_TAGS) && !StringUtils.containsAny(line,INTERNAL_START_TAGS)){

                for(int idx=0;idx<INTERNAL_END_TAGS.length;idx++){

                    if(line.indexOf(INTERNAL_END_TAGS[idx])>=0){
                        completedParagraph.add((INTERNAL_START_TAGS[idx]+line)
                            .replaceAll(EMPTY_TAG_REGEX,StringUtils.EMPTY));
                    }

                }

            }else {

                completedParagraph.add(line);
            }

        }

        return completedParagraph;

    }

}
