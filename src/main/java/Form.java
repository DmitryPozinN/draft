import controls.IntField;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import net.miginfocom.layout.AC;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import org.controlsfx.tools.Borders;
import org.tbee.javafx.scene.layout.MigPane;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by pozin_dn on 05.08.2018.
 */
public class Form extends BorderPane {

    private static final int MAX_RECT_WIDTH = 3500;
    private static final int MAX_RECT_HEIGHT = MAX_RECT_WIDTH;

    private static final int DEF_RECT_WIDTH = 1000;
    private static final int DEF_RECT_HEIGHT = DEF_RECT_WIDTH;

    private static final int PAPER_WIDTH = 210;
    private static final int PAPER_HEIGHT = 297;
    private static final int DEF_TOP_BOTTOM_PAPER_MARGIN = 3;
    private static final int DEF_LEFT_RIGHT_PAPER_MARGIN = DEF_TOP_BOTTOM_PAPER_MARGIN;
    private static final int MIN_HALF_WIDTH_BETWEEN_MIRRORED = 2;

    private static final int DEF_ADDITIVE_COUNT = 7;
    private static final int DEF_ADDITIVE_LEVEL = 0;
    private static final int DEF_ADDITIVE_LEFT = 100;
    private static final int DEF_ADDITIVE_RIGHT = DEF_ADDITIVE_LEFT;

    private static final int SIZE_TRAIT_LENGTH = 15;

    private static final double DEF_IMAGE_SCALE = 1.0;
    private static final Orientation DEF_ORIENTATION = Orientation.VERTICAL;

    private static final double RECT_BORDER_WIDTH = 2.0;
    private static final double ADDITIVE_WIDTH = RECT_BORDER_WIDTH / 2.0;
    private static final double SIZE_WIDTH = RECT_BORDER_WIDTH / 2.0;
    private static final int ADDITIVE_LABEL_LENGTH = 10;

    private static final double BIG_ARROW_WIDTH = SIZE_TRAIT_LENGTH / 4.0;
    private static final double BIG_ARROW_LENGTH = SIZE_TRAIT_LENGTH / 1.5;

    private static final double SMALL_ARROW_WIDTH = BIG_ARROW_WIDTH / 2.0;
    private static final double SMALL_ARROW_LENGTH = BIG_ARROW_LENGTH / 2.0;

    private boolean lockRedraw;

    private final PreView preView;
    private boolean fullScreen;

    public void setFullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen;
        if (fullScreen) {
            setCenter(preView.sheet);
            setRight(null);
        } else {
            preView.setPreview();
            setCenter(Borders.wrap(preView).lineBorder().innerPadding(10, 2, 0, 2).title("Предварительный просмотр").buildAll());
            setRight(Borders.wrap(new Control()).lineBorder().innerPadding(10, 2, 0, 2).title("Управление").buildAll());
        }
        Platform.runLater(preView::resizeSheet);
    }

    static class AdditiveInfo {

        enum Type {

            NONE("Нет"),

            VB("ВБ"),

            COUPLER("Стяжка"),

            BALLOONS("Шарики"),

            SHELF_HOLDER("Полкодержатель");

            Type(String name) {
                this.name = name;
            }

            @Override
            public String toString() {
                return name;
            }

            private String name;
        }

        private int idx;
        private int level;
        private int left;
        private int right;
        private Type type;
        private boolean valid;

        AdditiveInfo(int idx) {
            level = DEF_ADDITIVE_LEVEL;
            left = DEF_ADDITIVE_LEFT;
            right = DEF_ADDITIVE_RIGHT;
            type = Type.NONE;
            valid = true;
            this.idx = idx;
        }
    }

    private List<AdditiveInfo> additives;

    public Form() {
        getStylesheets().add("css/Form.css");

        additives = new ArrayList<>();

        preView = new PreView();
        setFullScreen(false);
    }

    private class PreView extends MigPane {

        private int width;
        private int height;
        private double scale;               //virtual (user) scale
        private double sheetScale;          //scale  sheet distance to form distance
        private double sheetWidth;
        private double sheetHeight;
        private boolean mirrored;
        private boolean sizeFromBottom;

        private int topBottomMargin;
        private int leftRightMargin;

        private double paperWidth;
        private double paperHeight;

        private final AnchorPane sheet;

        private Orientation orientation;

        private PreView() {
            super(new LC().align("center", "center"), new AC().grow());
            getStylesheets().add("css/Form.css");

            sheet = new AnchorPane();
            sheet.getStyleClass().add("sheet");
            sheet.getStyleClass().add("sheet-border");
            setPreview();

            widthProperty().addListener((observable, oldValue, newValue) -> resizeSheet());
            heightProperty().addListener((observable, oldValue, newValue) -> resizeSheet());

            width = DEF_RECT_WIDTH;
            height = DEF_RECT_HEIGHT;
            topBottomMargin = DEF_TOP_BOTTOM_PAPER_MARGIN;
            leftRightMargin = DEF_LEFT_RIGHT_PAPER_MARGIN;
            scale = DEF_IMAGE_SCALE;
            sizeFromBottom = false;
            orientation = DEF_ORIENTATION;
            paperWidth = getPaperWidth();
            paperHeight = getPaperHeight();
            resizeSheet();
            redraw();
        }

        private void setPreview() {
            if (!getChildren().contains(sheet)) {
                add(sheet, new CC().alignX("center").alignY("center"));
            }
        }

        private double getPaperWidth() {
            return orientation == Orientation.VERTICAL ? PAPER_WIDTH : PAPER_HEIGHT;
        }

        private double getPaperHeight() {
            return orientation == Orientation.VERTICAL ? PAPER_HEIGHT : PAPER_WIDTH;
        }

        private void resize(int width, int height) {
            if (this.width == width && this.height == height)
                return;
            this.width = width;
            this.height = height;
            redraw();
        }

        private void mirror(boolean value) {
            if (mirrored == value)
                return;
            mirrored = value;
            redraw();
        }

        private Line drawLine(double x1, double y1, double x2, double y2, double strokeWidth) {
            final Line line = new Line(x1, y1, x2, y2);
            line.setStrokeWidth(strokeWidth);
            sheet.getChildren().add(line);
            return line;
        }

        private Line drawRectLine(double x1, double y1, double x2, double y2) {
            return drawLine(x1, y1, x2, y2, RECT_BORDER_WIDTH);
        }

        private Line drawAdditiveLine(double x1, double y1, double x2, double y2) {
            return drawLine(x1, y1, x2, y2, ADDITIVE_WIDTH);
        }

        private Line drawSizeLine(double x1, double y1, double x2, double y2){
            return drawLine(x1, y1, x2, y2, SIZE_WIDTH);
        }

        private Line drawArrowedLine(double x1, double y1, double x2, double y2, int sizeLabel) {
            return drawArrowedLine(x1, y1, x2, y2, BIG_ARROW_LENGTH, BIG_ARROW_WIDTH, getSizeStr(sizeLabel), 10);
        }

        private Line drawSmallArrowedLine(double x1, double y1, double x2, double y2, int sizeLabel) {
            return drawArrowedLine(x1, y1, x2, y2, SMALL_ARROW_LENGTH, SMALL_ARROW_WIDTH, String.valueOf(sizeLabel), 8);
        }

        private Line drawArrowedLine(double x1, double y1, double x2, double y2, double arLength, double arWidth) {
            return drawArrowedLine(x1, y1, x2, y2, arLength, arWidth, true, true);
        }

        private Line drawArrowedLine(double x1, double y1, double x2, double y2, double arLength, double arWidth, boolean drawFirstArrow, boolean drawSecondArrow) {
            final double w = x2 - x1;
            final double h = y2 - y1;
            if (x2 != x1 || y2 != y1) {
                final double factor = arLength / Math.hypot(w, h);
                final double factorO = arWidth / Math.hypot(w, h);

                double dx = (w) * factor;
                double dy = (h) * factor;

                double ox = (w) * factorO;
                double oy = (h) * factorO;

                if (drawFirstArrow) {
                    drawSizeLine(x2, y2, x2 - dx + oy, y2 - dy - ox);
                    drawSizeLine(x2, y2, x2 - dx - oy, y2 - dy + ox);
                }

                if (drawSecondArrow) {
                    drawSizeLine(x1, y1, x1 + dx - oy, y1 + dy + ox);
                    drawSizeLine(x1, y1, x1 + dx + oy, y1 + dy - ox);
                }
            }

            return drawSizeLine(x1, y1, x2, y2);
        }

        private Line drawArrowedLine(double x1, double y1, double x2, double y2, double arLength, double arWidth, String sizeLabel, double fontSize) {
            final Line line = drawArrowedLine(x1, y1, x2, y2, arLength, arWidth);
            final Orientation orientation = x1 == x2 ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            final double w = x2 - x1;
            final double h = y2 - y1;
            final Label lbl = drawLabel(sizeLabel, orientation, fontSize);
            lbl.setPrefWidth(orientation == Orientation.HORIZONTAL ? w : h);
            lbl.setLayoutX(orientation == Orientation.HORIZONTAL ? x1 : x2 - h / 2.0 - 6);
            lbl.setLayoutY(orientation == Orientation.HORIZONTAL ? y2 - 0.7 * SIZE_TRAIT_LENGTH - 3: y1 + h / 2.0 - 8);
            return line;
        }

        private Line drawSmallArrowedLine(double x, double y1, double y2, int sizeLabel, boolean drawBottomArrow) {
            final Line line = drawArrowedLine(x, y1, x, y2, SMALL_ARROW_LENGTH, SMALL_ARROW_WIDTH, true, drawBottomArrow);
            final double h = y2 - y1;
            final Label lbl = drawLabel(String.valueOf(sizeLabel), Orientation.VERTICAL, 8);
            lbl.setRotate(90);
//            lbl.setPrefWidth(h);
            lbl.setLayoutX(x - 1);
            lbl.setLayoutY(y1 + h / 2.0 - 8);
            return line;
        }

        private Label drawLabel(String lbl, Orientation orientation, double fontSize) {
            final Label label = new Label(lbl);
            if (orientation == Orientation.VERTICAL) {
                label.setRotate(-90.0);
            }
            label.getStyleClass().add("center-text");
            label.setFont(Font.font(fontSize));
            sheet.getChildren().add(label);
            return label;
        }

        private Label drawLabel(String lbl, double layoutX, double layoutY, boolean left) {
            final Label label = drawLabel(lbl, Orientation.VERTICAL, 12);
            if (!left) {
                label.setRotate(90.0);
            }
            label.setLayoutX(layoutX);
            label.setLayoutY(layoutY);
            return label;
        }

        private String getSizeStr(int size) {
            return String.format("%d мм", size);
        }

        private void setSizeFromBottom(boolean value) {
            if (sizeFromBottom == value) {
                return;
            }
            sizeFromBottom = value;
            redraw();
        }

        private void redraw() {
            if (lockRedraw)
                return;

            sheet.getChildren().clear();

            additives.sort(Comparator.comparingInt(o -> o.level));

            final double scale = this.scale * sheetScale * Math.min(
                    (paperWidth) / (double) MAX_RECT_WIDTH,
                    (paperHeight) / (double) MAX_RECT_HEIGHT);

            final double wm = sheetScale * leftRightMargin;
            final double hm = sheetScale * topBottomMargin;
            final double tm = sheetScale * SIZE_TRAIT_LENGTH;

            final double[] lastY = {-1};
            final int[] lastLevel = {0};

            if (mirrored) {

                final double w = scale * width / 2.0;
                final double h = scale * height / 2.0;

                final double mwm = sheetScale * MIN_HALF_WIDTH_BETWEEN_MIRRORED;

                final boolean drawLeftRight = sheetWidth > 2 * (w + wm) + mwm;
                final boolean drawTopBottom = sheetHeight > h + 2 * hm;

                final double m = drawLeftRight ? (sheetWidth - 2 * w) / 3.0 : mwm;

                final double lx0 = drawLeftRight ? m : wm;
                final double lx1 = drawLeftRight ? m + w : (sheetWidth - m)/ 2.0;

                final double y0 = drawTopBottom ? (sheetHeight - h) / 2.0 : hm;
                final double y1 = sheetHeight - y0;

                final double rx0 = lx1 + m;
                final double rx1 = drawLeftRight ? rx0 + w : sheetWidth - wm;

                if (drawLeftRight) {
                    drawRectLine(lx0, y0, lx0, y1);
                    drawRectLine(rx1, y0, rx1, y1);
                }

                drawRectLine(lx1, y0, lx1, y1);
                drawRectLine(rx0, y0, rx0, y1);

                if (drawTopBottom) {
                    drawRectLine(lx0, y0, lx1, y0);
                    drawRectLine(lx0, y1, lx1, y1);
                    drawRectLine(rx0, y0, rx1, y0);
                    drawRectLine(rx0, y1, rx1, y1);
                }
                final boolean drawHeight = (drawTopBottom) && (sheetWidth > rx1 + tm + wm) && (h >= 3 * SIZE_TRAIT_LENGTH);
                final boolean drawWidth = (drawLeftRight) && (sheetHeight > y1 + tm + hm) &&  (w >= 3 * SIZE_TRAIT_LENGTH);

                final Function<AdditiveInfo, Boolean> drawAdditive = ai -> (ai.valid) && (ai.type != AdditiveInfo.Type.NONE) && (sheetHeight > scale * (height - ai.level) / 2.0 + 2 * hm);
                final boolean drawAdditives = drawHeight && additives.stream().anyMatch(drawAdditive::apply);

                final double ltax = lx0 - SIZE_TRAIT_LENGTH;
                final double lsax = ltax + 0.2 * SIZE_TRAIT_LENGTH;
                final double rtax = rx0 - SIZE_TRAIT_LENGTH;
                final double rsax = rtax + 0.2 * SIZE_TRAIT_LENGTH;

                if (drawHeight) {
                    double x2 = lx1 + SIZE_TRAIT_LENGTH;
                    double x3 = lx1 + 0.8 * SIZE_TRAIT_LENGTH;
                    drawSizeLine(lx1, y0, x2, y0);
                    drawSizeLine(lx1, y1, x2, y1);
                    drawArrowedLine(x3, y0, x3, y1, height);

                    x2 = rx1 + SIZE_TRAIT_LENGTH;
                    x3 = rx1 + 0.8 * SIZE_TRAIT_LENGTH;
                    drawSizeLine(rx1, y0, x2, y0);
                    drawSizeLine(rx1, y1, x2, y1);
                    drawArrowedLine(x3, y0, x3, y1, height);

                    if (drawAdditives) {
                        lastY[0] = y1;
                        drawSizeLine(ltax, y1, lx0, y1);
                        drawSizeLine(rtax, y1, rx0, y1);
                    }
                }

                if (drawWidth) {
                    final double y2 = y1 + SIZE_TRAIT_LENGTH;
                    final double y3 = y1 + 0.8 * SIZE_TRAIT_LENGTH;
                    drawSizeLine(rx0, y1, rx0, y2);
                    drawSizeLine(rx1, y1, rx1, y2);
                    drawArrowedLine(rx0, y3, rx1, y3, width);
                    drawSizeLine(lx0, y1, lx0, y2);
                    drawSizeLine(lx1, y1, lx1, y2);
                    drawArrowedLine(lx0, y3, lx1, y3, width);
                }

                final double hall = this.scale < 1.0 ? this.scale * ADDITIVE_LABEL_LENGTH / 2.0 : ADDITIVE_LABEL_LENGTH / 2.0;

                additives.forEach(ai -> {
                    if (drawAdditive.apply(ai)) {
                        final double y = y1 - scale * ai.level / 2.0;
                        final double ly1 = y - hall;
                        final double ly2 = y + SIZE_TRAIT_LENGTH;
                        final double llx1 = lx0 + scale * ai.left / 2.0;
                        final double llx2 = lx1 -  scale * ai.right / 2.0;
                        final double lrx1 = rx0 + scale * ai.right / 2.0;
                        final double lrx2 = rx1 -  scale * ai.left / 2.0;
                        drawAdditiveLine(lx0, y, lx1, y);
                        drawAdditiveLine(llx1, ly1, llx1, ly2);
                        drawAdditiveLine(llx2, ly1, llx2, ly2);
                        drawAdditiveLine(rx0, y, rx1, y);
                        drawAdditiveLine(lrx1, ly1, lrx1, ly2);
                        drawAdditiveLine(lrx2, ly1, lrx2, ly2);

                        final Label llbl = drawLabel(ai.type.toString(), Orientation.HORIZONTAL, 10);
                        llbl.setPrefWidth(lx1 - lx0);
                        llbl.setLayoutX(lx0);
                        llbl.setLayoutY(y - 15);

                        final Label rlbl = drawLabel(ai.type.toString(), Orientation.HORIZONTAL, 10);
                        rlbl.setPrefWidth(rx1 - rx0);
                        rlbl.setLayoutX(rx0);
                        rlbl.setLayoutY(y - 15);

                        final double ys = y + 0.8 * SIZE_TRAIT_LENGTH;
                        drawSmallArrowedLine(lx0, ys, llx1, ys, ai.left);
                        drawSmallArrowedLine(llx2, ys, lx1, ys, ai.right);
                        drawSmallArrowedLine(rx0, ys, lrx1, ys, ai.right);
                        drawSmallArrowedLine(lrx2, ys, rx1, ys, ai.left);

                        if(drawAdditives) {
                            drawSizeLine(ltax, y, lx0, y);
                            drawSizeLine(rtax, y, rx0, y);
                            if (lastY[0] > 0) {
                                drawSmallArrowedLine(lsax, lastY[0], y, sizeFromBottom ? ai.level : ai.level - lastLevel[0], !sizeFromBottom || lastY[0] == y1);
                                drawSmallArrowedLine(rsax, lastY[0], y, sizeFromBottom ? ai.level : ai.level - lastLevel[0], !sizeFromBottom || lastY[0] == y1);
                            }
                            lastY[0] = y;
                            lastLevel[0] = ai.level;
                        }
                   }
                });

                final boolean drawFront = (sheetWidth > rx1 + tm + wm + sheetScale * 3) && (h >= 3 * SIZE_TRAIT_LENGTH);
                if (drawFront) {
                    drawLabel("ПЕРЕД", rx1 + SIZE_TRAIT_LENGTH - 10, y0 + (y1 - y0) / 2.0 - 10, false);
                    drawLabel("ПЕРЕД", lx0 - SIZE_TRAIT_LENGTH - 25, y0 + (y1 - y0) / 2.0 - 3, true);
                }

            } else {
                final double w = scale * width;
                final double h = scale * height;

                final boolean drawLeftRight = sheetWidth > w + 2 * wm;
                final boolean drawTopBottom = sheetHeight > h + 2 * hm;

                if (!(drawLeftRight || drawTopBottom)) {
                    return;
                }

                final double x0 = drawLeftRight ? (sheetWidth - w) / 2.0 : wm;
                final double x1 = drawLeftRight ? x0 + w : sheetWidth - wm;

                final double y0 = drawTopBottom ? (sheetHeight - h) / 2.0 : hm;
                final double y1 = sheetHeight - y0;

                if (drawLeftRight) {
                    drawRectLine(x0, y0, x0, y1);
                    drawRectLine(x1, y0, x1, y1);
                }

                if (drawTopBottom) {
                    drawRectLine(x0, y0, x1, y0);
                    drawRectLine(x0, y1, x1, y1);
                }

                final boolean drawHeight = (drawTopBottom) && (sheetWidth > x1 + tm + wm) && (h >= 3 * SIZE_TRAIT_LENGTH);
                final boolean drawWidth = (drawLeftRight) && (sheetHeight > y1 + tm + hm) && (w >= 3 * SIZE_TRAIT_LENGTH);

                final Function<AdditiveInfo, Boolean> drawAdditive = ai -> (ai.valid) && (ai.type != AdditiveInfo.Type.NONE) && (sheetHeight > scale * (height - ai.level) + 2 * hm);
                final boolean drawAdditives = drawHeight && additives.stream().anyMatch(drawAdditive::apply);

                final double tax = x0 - SIZE_TRAIT_LENGTH;
                final double sax = tax + 0.2 * SIZE_TRAIT_LENGTH;

                if (drawHeight) {
                    final double x2 = x1 + SIZE_TRAIT_LENGTH;
                    final double x3 = x1 + 0.8 * SIZE_TRAIT_LENGTH;
                    drawSizeLine(x1, y0, x2, y0);
                    drawSizeLine(x1, y1, x2, y1);
                    drawArrowedLine(x3, y0, x3, y1, height);

                    if (drawAdditives) {
                            lastY[0] = y1;
                            drawSizeLine(tax, y1, x0, y1);
                    }
                }

                if (drawWidth) {
                    final double y2 = y1 + SIZE_TRAIT_LENGTH;
                    final double y3 = y1 + 0.8 * SIZE_TRAIT_LENGTH;
                    drawSizeLine(x0, y1, x0, y2);
                    drawSizeLine(x1, y1, x1, y2);
                    drawArrowedLine(x0, y3, x1, y3, width);
                }

                final double hall = this.scale < 1.0 ? this.scale * ADDITIVE_LABEL_LENGTH / 2.0 : ADDITIVE_LABEL_LENGTH / 2.0;

                additives.forEach(ai -> {
                    if (drawAdditive.apply(ai)) {
                        final double y = y1 - scale * ai.level;
                        final double ly1 = y - hall;
                        final double ly2 = y + SIZE_TRAIT_LENGTH;
                        final double lx1 = x0 + scale * ai.left;
                        final double lx2 = x1 - scale * ai.right;
                        drawAdditiveLine(x0, y, x1, y);
                        drawAdditiveLine(lx1, ly1, lx1, ly2);
                        drawAdditiveLine(lx2, ly1, lx2, ly2);

                        final Label lbl = drawLabel(ai.type.toString(), Orientation.HORIZONTAL, 10);
                        lbl.setPrefWidth(x1 - x0);
                        lbl.setLayoutX(x0);
                        lbl.setLayoutY(y - 15);

                        final double ys = y + 0.8 * SIZE_TRAIT_LENGTH;
                        drawSmallArrowedLine(x0, ys, lx1, ys, ai.left);
                        drawSmallArrowedLine(lx2, ys, x1, ys, ai.right);

                        if (drawAdditives) {
                            drawSizeLine(tax, y, x0, y);
                                if (lastY[0] > 0) {
                                    drawSmallArrowedLine(
                                            sax,
                                            lastY[0],
                                            y,
                                            sizeFromBottom ? ai.level : ai.level - lastLevel[0],
                                            !sizeFromBottom || lastY[0] == y1);
                                }
                            lastY[0] = y;
                            lastLevel[0] = ai.level;
                        }
                    }
                });

                final boolean drawFront = (sheetWidth > x1 + tm + wm + sheetScale * 3) && (h >= 3 * SIZE_TRAIT_LENGTH);
                if (drawFront) {
                    drawLabel("ПЕРЕД", x1 + SIZE_TRAIT_LENGTH - 10, y0 + (y1 - y0) / 2.0 - 10, false);
                }
            }
        }

        private void resizeSheet() {
            final double scaleX = (fullScreen ? Form.this.getWidth() : getWidth()) / paperWidth;
            final double scaleY = (fullScreen ? Form.this.getHeight() : getHeight()) / paperHeight;

            final double ss = Math.min(scaleX, scaleY);
            final double sw = ss * paperWidth;
            final double sh = ss * paperHeight;

            if (ss == sheetScale && sw == sheetWidth && sh == sheetHeight) {
                return;
            }

            sheetScale = ss;
            sheetWidth = sw;
            sheetHeight = sh;
            sheet.setPrefWidth(sheetWidth);
            sheet.setPrefHeight(sheetHeight);
            sheet.setMaxWidth(sheetWidth);
            sheet.setMaxHeight(sheetHeight);

            redraw();
        }

        private void setScale(double scale) {

            if (this.scale == scale) {
                return;
            }

            this.scale = scale;
            redraw();
        }

        private void setMargin(int topBottom, int leftRight) {

            if (topBottomMargin == topBottom && leftRightMargin == leftRight) {
                return;
            }

            topBottomMargin = topBottom;
            leftRightMargin = leftRight;
            redraw();
        }

        private void setOrientation(Orientation orientation) {
            if (this.orientation == orientation)
                return;

            this.orientation = orientation;
            paperWidth = getPaperWidth();
            paperHeight = getPaperHeight();
            resizeSheet();
        }
    }

    private class Control extends VBox {

        private final IntField ifHeight;
        private final IntField ifWidth;
        private final Button btnResize;

        private final IntField ifLeftRight;
        private final IntField ifTopBottom;
        private final Button btnApplyMargins;

        private final List<IntField> additiveLevel;
        private final List<IntField> additiveLeft;
        private final List<IntField> additiveRight;
        private final List<ComboBox<AdditiveInfo.Type>> additiveType;

        private void add(final Region region, final String title) {
            final Borders.LineBorders borders = Borders.wrap(region).lineBorder().outerPadding(5).innerPadding(3, 0, 2, 0);
            if (title != null && !title.isEmpty()) {
                borders.title(title);
            }
            getChildren().add(borders.buildAll());
        }

        private IntField createSizeField(int maxSize) {
            final IntField intField = new IntField(maxSize);
            intField.setPrefColumnCount(8);
            intField.valueProperty().addListener((observable, oldValue, newValue) -> onSizeValuesChanged());
            intField.validateProperty().addListener((observable, oldValue, newValue) -> onSizeValuesChanged());
            return intField;
        }

        private IntField createMarginField() {
            final IntField intField = new IntField(20);
            intField.getStyleClass().add("center-text");
            intField.setPrefColumnCount(4);
            intField.valueProperty().addListener((observable, oldValue, newValue) -> onMarginValuesChanged());
            intField.validateProperty().addListener((observable, oldValue, newValue) -> onMarginValuesChanged());
            return intField;
        }

        private void updateValidFlag(AdditiveInfo ai) {
            ai.valid = additiveLeft.get(ai.idx).validateProperty().get()
                    && additiveRight.get(ai.idx).validateProperty().get()
                    && additiveLevel.get(ai.idx).validateProperty().get();
        }

        private void addAdditive(final MigPane paneAdditive) {
            final AdditiveInfo ai = new AdditiveInfo(additives.size());
            additives.add(ai);

            final IntField ifLevel = new IntField(ifHeight.valueProperty());
            paneAdditive.add(ifLevel);
            ifLevel.setValue(ai.level);
            ifLevel.setPrefColumnCount(3);
            ifLevel.valueProperty().addListener((observable, oldValue, newValue) -> {
                ai.level = newValue;
                updateValidFlag(ai);
                preView.redraw();
            });
            ifLevel.validateProperty().addListener((observable, oldValue, newValue) -> {
                updateValidFlag(ai);
                preView.redraw();
            });
            additiveLevel.add(ifLevel);

            final IntField ifLeft = new IntField(ifWidth.valueProperty());
            paneAdditive.add(ifLeft);
            ifLeft.setValue(ai.left);
            ifLeft.setPrefColumnCount(3);
            ifLeft.valueProperty().addListener((observable, oldValue, newValue) -> {
                ai.left = newValue;
                updateValidFlag(ai);
                preView.redraw();
            });
            ifLeft.validateProperty().addListener((observable, oldValue, newValue) -> {
                updateValidFlag(ai);
                preView.redraw();
            });
            additiveLeft.add(ifLeft);

            final IntField ifRight = new IntField(ifWidth.valueProperty());
            paneAdditive.add(ifRight);
            ifRight.setValue(ai.right);
            ifRight.setPrefColumnCount(3);
            ifRight.valueProperty().addListener((observable, oldValue, newValue) -> {
                ai.right = newValue;
                updateValidFlag(ai);
                preView.redraw();
            });
            ifRight.validateProperty().addListener((observable, oldValue, newValue) -> {
                updateValidFlag(ai);
                preView.redraw();
            });
            additiveRight.add(ifRight);

            final ComboBox<AdditiveInfo.Type> cbType = new ComboBox<>(FXCollections.observableArrayList(AdditiveInfo.Type.values()));
            paneAdditive.add(cbType);
            cbType.setValue(ai.type);
            cbType.setMaxWidth(130);
            cbType.valueProperty().addListener((observable, oldValue, newValue) -> {
                ai.type = newValue;
                preView.redraw();
            });
            additiveType.add(cbType);
        }

        private AnchorPane getClonedPane() {
            final AnchorPane res = new AnchorPane();
            res.getStylesheets().add("css/Form.css");
            res.getStyleClass().add("sheet");

            final AnchorPane src = preView.sheet;

            res.setMinWidth(src.getMinWidth());
            res.setMinHeight(src.getMinHeight());
            res.setMaxWidth(src.getMaxWidth());
            res.setMaxHeight(src.getMaxHeight());
            res.setPrefWidth(src.getPrefWidth());
            res.setPrefHeight(src.getPrefHeight());

            for (Node node : src.getChildren()) {
                if (node instanceof Line) {
                    final Line line = (Line) node;
                    final Line nline = new Line(line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
                    res.getChildren().add(nline);
                    nline.setStrokeWidth(line.getStrokeWidth());
                    nline.getStyleClass().addAll(line.getStyleClass());
                } else if (node instanceof Label) {
                    final Label lbl = (Label) node;
                    final Label nlbl = new Label(lbl.getText());
                    res.getChildren().add(nlbl);
                    nlbl.getStyleClass().addAll(lbl.getStyleClass());
                    nlbl.getStyleClass().add("center-text");
                    nlbl.setFont(lbl.getFont());
                    nlbl.setRotate(lbl.getRotate());
                    nlbl.setLayoutX(lbl.getLayoutX());
                    nlbl.setLayoutY(lbl.getLayoutY());
                    nlbl.setPrefWidth(lbl.getPrefWidth());
                }
            }

            return  res;
        }

        Control() {
            super(0);

            additiveLeft = new ArrayList<>();
            additiveRight = new ArrayList<>();
            additiveLevel = new ArrayList<>();
            additiveType = new ArrayList<>();

            //region page
            final MigPane panePage = new MigPane(new LC().align("center", "center").wrapAfter(4), new AC().grow(100, 0, 1, 2, 3), new AC().grow().fill());
            add(panePage, "Параметры страницы");

            final RadioButton rbPortrait = new RadioButton("книжная");
            rbPortrait.setSelected(true);
            panePage.add(rbPortrait, new CC().spanX(2).alignX("center"));

            final RadioButton rbLandscape = new RadioButton("альбомная");
            rbLandscape.setSelected(false);
            panePage.add(rbLandscape, new CC().spanX(2).alignX("center"));

            final ToggleGroup tgOrientation = new ToggleGroup();
            rbPortrait.setToggleGroup(tgOrientation);
            rbLandscape.setToggleGroup(tgOrientation);
            tgOrientation.selectedToggleProperty().addListener((observable, oldValue, newValue)
                    -> preView.setOrientation(newValue == rbPortrait
                    ? Orientation.VERTICAL
                    : Orientation.HORIZONTAL)
            );

            panePage.add(new Separator(Orientation.HORIZONTAL), new CC().grow().spanX(4));

            final Label lblScale = new Label("Масштаб:");
            lblScale.setAlignment(Pos.CENTER_LEFT);
            lblScale.setTextAlignment(TextAlignment.LEFT);
            panePage.add(lblScale);

            final Slider scale = new Slider(0.0, 5.0, DEF_IMAGE_SCALE);
            scale.setMajorTickUnit(1.0);
            scale.setShowTickMarks(true);
            scale.setShowTickLabels(true);
            scale.valueProperty().addListener((observable, oldValue, newValue) -> preView.setScale(newValue.doubleValue()));
            panePage.add(scale, new CC().grow().spanX(3));

            panePage.add(new Separator(Orientation.HORIZONTAL), new CC().grow().spanX(4));

            final Label lblLeft = new Label("Левый и правый отступ");
            panePage.add(lblLeft, new CC().grow().spanX(3));

            ifLeftRight = createMarginField();
            panePage.add(ifLeftRight, new CC().grow());

            final Label lblRight = new Label("Верхний и нижний отступ");
            panePage.add(lblRight, new CC().grow().spanX(3));

            ifTopBottom = createMarginField();
            panePage.add(ifTopBottom, new CC().grow());

            btnApplyMargins = new Button("Применить");
            btnApplyMargins.setOnAction(ignored -> {
                preView.setMargin(ifTopBottom.getValue(), ifLeftRight.getValue());
                btnApplyMargins.setDisable(true);
            });
            panePage.add(btnApplyMargins, new CC().grow().spanX(4));

            ifTopBottom.setValue(DEF_TOP_BOTTOM_PAPER_MARGIN);
            ifLeftRight.setValue(DEF_LEFT_RIGHT_PAPER_MARGIN);
            btnApplyMargins.setDisable(true);
            //endregion

            //region resize
            final MigPane paneSize = new MigPane(new LC().align("center", "center").wrapAfter(3), new AC().fill());
            add(paneSize, "Размеры");

            final Label lblHeight = new Label("Высота");
            lblHeight.getStyleClass().add("center-text");
            paneSize.add(lblHeight);

            paneSize.add(new Pane());

            final Label lblWidth = new Label("Ширина");
            lblWidth.getStyleClass().add("center-text");
            paneSize.add(lblWidth);

            ifHeight = createSizeField(MAX_RECT_HEIGHT);
            paneSize.add(ifHeight);

            final Label lblX = new Label("  x  ");
            lblX.getStyleClass().add("center-text");
            paneSize.add(lblX);

            ifWidth = createSizeField(MAX_RECT_WIDTH);
            paneSize.add(ifWidth);

            btnResize = new Button("Изменить");
            btnResize.setOnAction(ignored -> {
                final int w = ifWidth.getValue();
                final int h = ifHeight.getValue();
                preView.resize(w, h);
                btnResize.setDisable(true);
                lockRedraw = true;
                boolean rd = false;
                for (int i = 0; i < additives.size(); i++) {
                    final AdditiveInfo ai = additives.get(i);
                    if (ai.level > h) {
                        rd = true;
                        additiveLevel.get(i).setValue(h);
                    }
                    if (ai.left > w) {
                        rd = true;
                        additiveLeft.get(i).setValue(w);
                    }
                    if (ai.right > w) {
                        rd = true;
                        additiveRight.get(i).setValue(w);
                    }
                }
                lockRedraw = false;
                if (rd) {
                    preView.redraw();
                }
            });
            paneSize.add(btnResize, new CC().spanX(3));

            ifWidth.setValue(DEF_RECT_WIDTH);
            ifHeight.setValue(DEF_RECT_HEIGHT);
            btnResize.setDisable(true);
            //endregion

            //region mirror
            final CheckBox cbMirror = new CheckBox("Зеркально");
            add(cbMirror, null);
            cbMirror.getStyleClass().add("center-text");
            cbMirror.selectedProperty().addListener((observable, oldValue, newValue) -> preView.mirror(newValue));
            //endregion

            //region additives levels from button
            final CheckBox chSizeFromBottom = new CheckBox("уровень присадки с низу");
            add(chSizeFromBottom, null);
            chSizeFromBottom.setSelected(false);
            chSizeFromBottom.getStyleClass().add("center-text");
            chSizeFromBottom.selectedProperty().addListener((observable, oldValue, newValue) -> preView.setSizeFromBottom(newValue));
            //endregion

            //region additive
            final MigPane paneAdditive = new MigPane(new LC().align("center", "center").wrapAfter(4), new AC());
            add(paneAdditive, "Присадки");

            paneAdditive.add(new Label("Уровень"), new CC().alignX("center"));
            paneAdditive.add(new Label("Слева"), new CC().alignX("center"));
            paneAdditive.add(new Label("Справа"), new CC().alignX("center"));
            paneAdditive.add(new Label("Тип"), new CC().alignX("center"));

            for (int i = 0; i < DEF_ADDITIVE_COUNT; i++) {
                addAdditive(paneAdditive);
            }

            //endregion

            //region clear and print
            final MigPane mpCtrl = new MigPane(new LC(), new AC().grow().fill(), new AC().grow().fill());
            add(mpCtrl, null);

            final Button btnClear = new Button("Очистить");
            mpCtrl.add(btnClear);
            btnClear.getStyleClass().add("center-text");
            btnClear.setOnAction(ignored -> {
                final ButtonType btOk = new ButtonType("Да, без сомнений");
                final ButtonType btCancel = new ButtonType("Нет, еще рано");
                final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Вы уверены что хотите все очистить?", btOk, btCancel);
                alert.initStyle(StageStyle.UTILITY);
                alert.setHeaderText("Сброс параметров");

                if (alert.showAndWait().get() == btCancel) {
                    return;
                }

                lockRedraw = true;
                ifWidth.setValue(DEF_RECT_WIDTH);
                ifHeight.setValue(DEF_RECT_HEIGHT);
                cbMirror.setSelected(false);
                for (int i = 0; i < DEF_ADDITIVE_COUNT; i++) {
                    additiveLeft.get(i).setValue(DEF_ADDITIVE_LEFT);
                    additiveRight.get(i).setValue(DEF_ADDITIVE_RIGHT);
                    additiveLevel.get(i).setValue(DEF_ADDITIVE_LEVEL);
                    additiveType.get(i).setValue(AdditiveInfo.Type.NONE);
                }
                lockRedraw = false;
                preView.redraw();
            });

            final Button btnPrint = new Button("Печать");
            mpCtrl.add(btnPrint);
            btnPrint.getStyleClass().add("center-text");
            btnPrint.setOnAction(ignored -> {
                final Printer dp = Printer.getDefaultPrinter();
                if (dp == null) {
                    final ButtonType btOk = new ButtonType("Печально, пойду проверю провода");
                    final Alert alert = new Alert(Alert.AlertType.ERROR, "Печать не удалась, не найдено ни одного принтера", btOk);
                    alert.initStyle(StageStyle.UTILITY);
                    alert.setHeaderText("Принтер не найден");
                    alert.showAndWait();
                } else {
                    final PageLayout pageLayout = dp.createPageLayout(
                            Paper.A4,
                            preView.orientation == Orientation.HORIZONTAL
                                    ? PageOrientation.LANDSCAPE
                                    : PageOrientation.PORTRAIT,
                            0.0, 0.0, 0.0, 0.0);

                    final AnchorPane node = getClonedPane();
                    final double scaleX = pageLayout.getPrintableWidth() / node.getPrefWidth();
                    final double scaleY = pageLayout.getPrintableHeight() / node.getPrefHeight();
                    node.getTransforms().add(new Scale(scaleX, scaleY));

                    final PrinterJob job = PrinterJob.createPrinterJob();
                    if (job != null) {
                        boolean success = job.printPage(pageLayout, node);
                        if (success) {
                            job.endJob();
                        }
                    }
                }
            });

            //endregion
        }

        private void error(Exception e) {
            final ButtonType btOk = new ButtonType("Печально, пойду звать программиста");
            final Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), btOk);
            alert.initStyle(StageStyle.UTILITY);
            alert.setHeaderText("Непонятная ошибка");
            alert.showAndWait();
        }

        private void onSizeValuesChanged() {
            final boolean valid = ifWidth.validateProperty().get() && ifHeight.validateProperty().get();
            if (valid) {
                final boolean changed = ifWidth.getValue() != preView.width || ifHeight.getValue() != preView.height;
                btnResize.setDisable(!changed);
            } else {
                btnResize.setDisable(true);
            }
        }

        private void onMarginValuesChanged() {
            final boolean valid = ifLeftRight.validateProperty().get() && ifTopBottom.validateProperty().get();
            if (valid) {
                final boolean changed = ifLeftRight.getValue() != preView.leftRightMargin || ifTopBottom.getValue() != preView.topBottomMargin;
                btnApplyMargins.setDisable(!changed);
            } else {
                btnApplyMargins.setDisable(true);
            }
        }
    }
}
