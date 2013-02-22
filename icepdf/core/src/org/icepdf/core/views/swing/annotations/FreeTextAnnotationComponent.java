/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.views.swing.annotations;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.commands.DrawCmd;
import org.icepdf.core.pobjects.graphics.commands.TextSpriteDrawCmd;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * The FreeTextAnnotationComponent encapsulates a FreeTextAnnotation objects.  It
 * also provides basic editing functionality such as resizing, moving and change
 * the border color and style as well as the fill color.
 * <p/>
 * The Viewer RI implementation contains a FreeTextAnnotationPanel class which
 * can edit the various properties of this component.
 * <p/>
 * The FreeTextAnnotationComponent is slightly more complex then the other
 * annotations components.  Most annotations let the page pain the annotation
 * but in this cse FreeTextAnnotationComponent paints itself by creating a
 * JTextArea component that is made to look like the respective annotations
 * appearance stream.
 *
 * @see org.icepdf.ri.common.annotation.FreeTextAnnotationPanel
 * @since 5.0
 */
public class FreeTextAnnotationComponent extends MarkupAnnotationComponent
        implements PropertyChangeListener {

    private static final Logger logger =
            Logger.getLogger(FreeTextAnnotation.class.toString());

    private JTextArea freeTextPane;

    private boolean contentTextChange;

    private FreeTextAnnotation freeTextAnnotation;

    public FreeTextAnnotationComponent(Annotation annotation, DocumentViewController documentViewController,
                                       final AbstractPageViewComponent pageViewComponent,
                                       final DocumentViewModel documentViewModel) {
        super(annotation, documentViewController, pageViewComponent, documentViewModel);
        isEditable = true;
        isRollover = false;
        isMovable = true;
        isResizable = true;
        isShowInvisibleBorder = false;

        freeTextAnnotation = (FreeTextAnnotation) annotation;
        // todo break out to interface so we can change the visibility more easily
        freeTextAnnotation.setHideRenderedOutput(true);

        // update the bounds to be bit larger as the border padding can obscure
        // the content
        Rectangle bounds = getBounds();
        bounds.setRect(bounds.x - 10, bounds.y - 10, bounds.width + 10, bounds.height + 10);
        setBounds(bounds);

        // update the shapes array pruning any text glyphs as well as
        // extra any useful font information for the editing of this annotation.
        if (annotation.getShapes() != null) {
            ArrayList<DrawCmd> shapes = annotation.getShapes().getShapes();
            DrawCmd cmd;
            for (int i = 0; i < shapes.size(); i++) {
                cmd = shapes.get(i);
                if (cmd instanceof TextSpriteDrawCmd) {
                    // grab the font reference
                    TextSprite tmp = ((TextSpriteDrawCmd) cmd).getTextSprite();
                    FontFile font = tmp.getFont();
                    freeTextAnnotation.setFontSize((int) font.getSize());
                    freeTextAnnotation.setFontStyle(font.getStyle());
                    freeTextAnnotation.setFontColor(tmp.getStrokeColor());
                    // remove all text.
                    shapes.remove(i);
                }
            }
            ((FreeTextAnnotation) annotation).clearShapes();
        }
        // create the textArea to display the text.
        freeTextPane = new JTextArea() {
            @Override
            protected void paintComponent(Graphics g) {
                float zoom = documentViewModel.getViewZoom();
                Graphics2D g2 = (Graphics2D) g;
                g2.scale(zoom, zoom);
                // paint the component at the scale of the page.
                super.paintComponent(g2);
            }
        };
        // line wrap false to force users to add line breaks.
        freeTextPane.setLineWrap(false);
        freeTextPane.setBackground(new Color(0, 0, 0, 0));
        freeTextPane.setMargin(new Insets(0, 0, 0, 0));
        // lock the field until the correct tool selects it.
        freeTextPane.setEditable(false);
        freeTextPane.setText(freeTextAnnotation.getContents());

        // setup change listener so we now when to set the annotations AP stream
        freeTextPane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                contentTextChange = true;
            }

            public void removeUpdate(DocumentEvent e) {
                contentTextChange = true;
            }

            public void changedUpdate(DocumentEvent e) {
                contentTextChange = true;
            }
        });

        GridLayout grid = new GridLayout(1, 1, 0, 0);
        this.setLayout(grid);
        this.add(freeTextPane);

        // add a focus management listener.
        KeyboardFocusManager focusManager =
                KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addPropertyChangeListener(this);

        setAppearanceStream();
        freeTextPane.validate();
    }

    public void setAppearanceStream() {
        // copy over annotation properties from the free text annotation.
        freeTextPane.setFont(new Font(freeTextAnnotation.getFontName(),
                freeTextAnnotation.getFontStyle(),
                freeTextAnnotation.getFontSize()));
        freeTextPane.setForeground(freeTextAnnotation.getFontColor());

        if (freeTextAnnotation.isFillType()) {
            freeTextPane.setOpaque(true);
            freeTextPane.setBackground(freeTextAnnotation.getFillColor());
        } else {
            freeTextPane.setOpaque(false);
        }
        if (freeTextAnnotation.isStrokeType()) {
            if (freeTextAnnotation.getBorderStyle().isStyleSolid()) {
                freeTextPane.setBorder(BorderFactory.createLineBorder(
                        freeTextAnnotation.getColor(),
                        (int) freeTextAnnotation.getBorderStyle().getStrokeWidth()));
            } else if (freeTextAnnotation.getBorderStyle().isStyleDashed()) {
                freeTextPane.setBorder(
                        new DashedBorder(freeTextAnnotation.getBorderStyle(),
                                freeTextAnnotation.getColor()));
            }
        } else {
            freeTextPane.setBorder(BorderFactory.createEmptyBorder());
        }

        String content = null;
        try {
            content = freeTextPane.getDocument().getText(0,
                    freeTextPane.getDocument().getLength());
        } catch (BadLocationException e) {
            logger.warning("Error getting rich text.");
        }
        Rectangle tBbox = convertToPageSpace(getBounds());

        // generate the shapes
        freeTextAnnotation.setAppearanceStream(tBbox);
        freeTextAnnotation.setContents(content);
        freeTextAnnotation.setRichText(freeTextPane.getText());
        freeTextPane.revalidate();
    }

    @Override
    public void mouseDragged(MouseEvent me) {
        super.mouseDragged(me);
        Rectangle tBbox = convertToPageSpace(getBounds());
        annotation.syncBBoxToUserSpaceRectangle(tBbox);
        setAppearanceStream();
    }

    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();

        if ("focusOwner".equals(prop) &&
                oldValue instanceof JTextArea) {
            JTextArea freeText = (JTextArea) oldValue;
            if (freeText.equals(freeTextPane)) {
                freeText.setEditable(false);
                if (contentTextChange) {
                    contentTextChange = false;
                    setAppearanceStream();
                }
            }
        } else if ("focusOwner".equals(prop) &&
                newValue instanceof JTextArea) {
            JTextArea freeText = (JTextArea) newValue;
            if (freeText.equals(freeTextPane) &&
                    documentViewModel.getViewToolMode() == DocumentViewModel.DISPLAY_TOOL_SELECTION) {
                freeText.setEditable(true);
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent me) {
        super.mouseMoved(me);

    }

    @Override
    public void paintComponent(Graphics g) {
        Page currentPage = pageViewComponent.getPage();
        if (currentPage != null && currentPage.isInitiated()) {
            // update bounds for for component
            if (currentZoom != documentViewModel.getViewZoom() ||
                    currentRotation != documentViewModel.getViewRotation()) {
                validate();
            }
        }
    }

    @Override
    public void resetAppearanceShapes() {

    }

    public String clearXMLHeader(String strXML) {
        String regExp = "[<][?]\\s*[xml].*[?][>]";
        strXML = strXML.replaceFirst(regExp, "");
        return strXML;
    }

    public class MyHtml2Text extends HTMLEditorKit.ParserCallback {
        StringBuffer s;

        public MyHtml2Text() {
        }

        public void parse(Reader in) throws IOException {
            s = new StringBuffer();
            ParserDelegator delegator = new ParserDelegator();
            delegator.parse(in, this, Boolean.TRUE);
        }

        public void handleText(char[] text, int pos) {
            s.append(text);
            s.append("\n");
        }

        public String getText() {
            return s.toString();
        }
    }

    /**
     * Convert the shapes that make up the annotation to page space so that
     * they will scale correctly at different zooms.
     *
     * @return transformed bbox.
     */
    protected Rectangle convertToPageSpace(Rectangle rect) {
        Page currentPage = pageViewComponent.getPage();
        AffineTransform at = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        try {
            at = at.createInverse();
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }
        // convert the two points as well as the bbox.
        Rectangle tBbox = new Rectangle(rect.x, rect.y,
                rect.width, rect.height);

        tBbox = at.createTransformedShape(tBbox).getBounds();

        return tBbox;

    }

    private class DashedBorder extends AbstractBorder {
        private BasicStroke stroke;
        private Color color;

        public DashedBorder(BorderStyle borderStyle, Color color) {
            int thickness = (int) borderStyle.getStrokeWidth();
            this.stroke = new BasicStroke(thickness,
                    BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER,
                    thickness * 2.0f,
                    freeTextAnnotation.getBorderStyle().getDashArray(),
                    0.0f);
            this.color = color;
        }

        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            float size = this.stroke.getLineWidth();
            if (size > 0.0f) {
                g = g.create();
                if (g instanceof Graphics2D) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setStroke(this.stroke);
                    g2d.setPaint(color != null ? color : c == null ? null : c.getForeground());
                    g2d.draw(new Rectangle2D.Float(x + size / 2, y + size / 2, width - size, height - size));
                }
                g.dispose();
            }
        }

        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.top = insets.right = insets.bottom =
                    (int) this.stroke.getLineWidth();
            return insets;
        }

    }
}