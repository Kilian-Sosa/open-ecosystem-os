package com.openecosystem.os.pdfhelper;

import java.awt.geom.Point2D;
import java.io.IOException;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.util.Matrix;

final class RasterCoverageAnalyzer extends PDFGraphicsStreamEngine {

  private final double pageArea;
  private double aggregateArea;
  private boolean singleSubstantial;

  private RasterCoverageAnalyzer(PDPage page) {
    super(page);
    PDRectangle crop = page.getCropBox();
    pageArea = Math.max(1D, crop.getWidth() * crop.getHeight());
  }

  static boolean hasSubstantialRaster(PDPage page) throws IOException {
    RasterCoverageAnalyzer analyzer = new RasterCoverageAnalyzer(page);
    analyzer.processPage(page);
    return analyzer.singleSubstantial || Math.min(analyzer.aggregateArea, analyzer.pageArea) >= analyzer.pageArea * .40D;
  }

  @Override
  public void drawImage(PDImage image) {
    Matrix matrix = getGraphicsState().getCurrentTransformationMatrix();
    double area = Math.abs(matrix.getScalingFactorX() * matrix.getScalingFactorY());
    aggregateArea += Math.min(area, pageArea);
    singleSubstantial |= area >= pageArea * .25D;
  }

  @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}
  @Override public void clip(int windingRule) {}
  @Override public void moveTo(float x, float y) {}
  @Override public void lineTo(float x, float y) {}
  @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}
  @Override public Point2D getCurrentPoint() { return null; }
  @Override public void closePath() {}
  @Override public void endPath() {}
  @Override public void strokePath() {}
  @Override public void fillPath(int windingRule) {}
  @Override public void fillAndStrokePath(int windingRule) {}
  @Override public void shadingFill(COSName shadingName) {}
}
