/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.print;

import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints any given area of a node to multiple pages
 */
public class NodePrinter {

  private static final double SCREEN_TO_PRINT_DPI = 72d / 96d;

  private double scale = 1.0f;

  /**
   * This rectangle determines the portion to print in the world coordinate system.
   */
  private Rectangle printRectangle;

  /**
   * Prints the given node.
   * @param job The printer job which has the configurations for the page layout etc. and does the actual printing.
   * @param showPrintDialog Whether or not the print dialog needs to be shown prior to printing.
   * @param node The content to print.
   * @return <code>true</code> if everything was printed, <code>false</code> otherwise
   */
  public boolean print(PrinterJob job, boolean showPrintDialog, Node node) {

    // bring up the print dialog in which the user can choose the printer etc.
    Window window = node.getScene() != null ? node.getScene().getWindow() : null;

    if (!showPrintDialog || job.showPrintDialog(window)) {

      PageLayout pageLayout = job.getJobSettings().getPageLayout();      
      double pageWidth = pageLayout.getPrintableWidth();
      double pageHeight = pageLayout.getPrintableHeight();

      PrintInfo printInfo = getPrintInfo(pageLayout);

      double printRectX = this.printRectangle.getX();
      double printRectY = this.printRectangle.getY();
      double printRectWith = this.printRectangle.getWidth();
      double printRectHeight = this.printRectangle.getHeight();

      // the following is suboptimal in many ways but needed for the sake of demonstration.
      // there need to be transformations made on the node so we store them and restore them later.
      // this is bad when the node is embedded somewhere in the scene graph because the size changes
      // will trigger updates and at least lead to "flickering".
      // in a real world application there should be another way to construct a node object
      // specifically for printing.

      // store old transformations and clip of the node
      Node oldClip = node.getClip();
      List<Transform> oldTransforms = new ArrayList<>(node.getTransforms());
      // set the printingRectangle bounds as clip
      node.setClip(new javafx.scene.shape.Rectangle(printRectX, printRectY,
          printRectWith, printRectHeight));

      int columns = printInfo.getColumnCount();
      int rows = printInfo.getRowCount();

      // by adjusting the scale, you can force the contents to be printed one page for example
      double localScale = printInfo.getScale();

      node.getTransforms().add(new Scale(localScale, localScale));
      // move to 0,0
      node.getTransforms().add(new Translate(-printRectX, -printRectY));

      // the transform that moves the node to fit the current printed page in the grid
      Translate gridTransform = new Translate();
      node.getTransforms().add(gridTransform);

      // for each page, move the node into position by adjusting the transform
      // and call the print page method of the PrinterJob
      boolean success = true;
      for (int row = 0; row < rows; row++) {
        for (int col = 0; col < columns; col++) {
          gridTransform.setX(-col * pageWidth / localScale);
          gridTransform.setY(-row * pageHeight / localScale);

          success &= job.printPage(pageLayout, node);
        }
      }
      // restore the original transformation and clip values
      node.getTransforms().clear();
      node.getTransforms().addAll(oldTransforms);
      node.setClip(oldClip);
      return success;
    }
    return false;
  }

  /**
   * Returns a scale factor to apply for printing.
   * A value of <code>0.72</code> makes <code>96</code> units in the world coordinate system appear exactly one inch long.
   * The default value is <code>1.0</code>.
   */
  public double getScale() {
    return scale;
  }

  /**
   * Sets a scale factor to apply for printing.
   * A value of <code>0.72</code> makes <code>96</code> units in the world coordinate system appear exactly one inch long.
   * The default value is <code>1.0</code>.
   */
  public void setScale(final double scale) {
    this.scale = scale;
  }

  /**
   * Returns the rectangle that will be printed.
   * This rectangle determines the portion of the node to print in the world coordinate system.
   * @return a rectangle in the world coordinate system that defines the area of the contents of the
   *                       node to print.
   */
  public Rectangle getPrintRectangle() {
    return printRectangle;
  }

  /**
   * Sets the rectangle that will be printed.
   * This rectangle determines the portion of the node to print in the world coordinate system.
   * @param printRectangle a rectangle in the world coordinate system that defines the area of the contents of the
   *                       node to print.
   */
  public void setPrintRectangle(final Rectangle printRectangle) {
    this.printRectangle = printRectangle;
  }

  /**
   * Determines the scale and the number of rows and columns needed to print the determined contents of the component
   * @param pageLayout the {@link javafx.print.PageLayout} that defines the printable area of a page.
   * @return a PrintInfo instance that encapsulates the computed values for scale, number of rows and columns.
   */
  public PrintInfo getPrintInfo(final PageLayout pageLayout) {

    double contentWidth = pageLayout.getPrintableWidth();
    double contentHeight = pageLayout.getPrintableHeight();

    double localScale = getScale() * SCREEN_TO_PRINT_DPI;

    final Rectangle printRect = getPrintRectangle();
    final double width = printRect.getWidth() * localScale;
    final double height = printRect.getHeight() * localScale;

    // calculate how many pages we need dependent on the size of the content and the page.
    int cCount = (int) Math.ceil((width) / contentWidth);
    int rCount = (int) Math.ceil((height) / contentHeight);

    return new PrintInfo(localScale, rCount, cCount);
  }

  /**
   * Encapsulates information for printing with a specific {@link javafx.print.PageLayout},
   * i.e. the scale dependent on the screen DPI as well as the number of rows and columns for poster printing.
   */
  public static class PrintInfo {
    final double scale;
    final int rowCount;
    final int columnCount;

    /**
     * Constructs a new PrintInfo instance.
     * @param scale The scale of the content.
     * @param rowCount The number of rows that are needed to print the content completely with the {@link javafx.print.PageLayout}.
     * @param columnCount The number of columns that are needed to print the content completely with the {@link javafx.print.PageLayout}.
     */
    public PrintInfo(final double scale, final int rowCount, final int columnCount) {
      this.scale = scale;
      this.rowCount = rowCount;
      this.columnCount = columnCount;
    }

    public double getScale() {
      return scale;
    }

    public int getRowCount() {
      return rowCount;
    }

    public int getColumnCount() {
      return columnCount;
    }
  }
}
