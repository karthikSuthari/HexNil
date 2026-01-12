package com.example.hexnil.report;

import android.content.Context;
import android.graphics.pdf.PdfDocument;
import android.os.ParcelFileDescriptor;

import com.example.hexnil.model.RollupMetrics;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Exports rollup metrics to CSV and lightweight PDF summaries.
 */
public class ReportExporter {

    public void exportCsv(RollupMetrics daily, RollupMetrics weekly, ParcelFileDescriptor pfd) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write("label,screenOnDrain,screenOffDrain,idleDrain,avgChargeMinutes,avgChargeSpeed,maxTemp,avgTemp,avgHealth,riskScore,severity\n");
            writeLine(writer, daily);
            writeLine(writer, weekly);
        }
    }

    private void writeLine(OutputStreamWriter writer, RollupMetrics r) throws IOException {
        if (r == null) return;
        writer.write(r.label + "," + r.screenOnDrain + "," + r.screenOffDrain + "," + r.idleDrain + "," +
                r.avgChargeMinutes + "," + r.avgChargeSpeed + "," + r.maxTemp + "," + r.avgTemp + "," +
                r.avgHealth + "," + r.riskScore + "," + r.severity + "\n");
    }

    public void exportPdf(Context context, RollupMetrics daily, RollupMetrics weekly, ParcelFileDescriptor pfd) throws IOException {
        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(612, 792, 1).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        android.graphics.Canvas canvas = page.getCanvas();
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTextSize(14f);
        int y = 40;
        canvas.drawText("HexNil Battery Report", 40, y, paint); y += 24;
        y = drawRollup(canvas, paint, "Daily", daily, y);
        y = drawRollup(canvas, paint, "Weekly", weekly, y + 16);
        doc.finishPage(page);
        try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor())) {
            doc.writeTo(fos);
        } finally {
            doc.close();
        }
    }

    private int drawRollup(android.graphics.Canvas canvas, android.graphics.Paint paint, String title, RollupMetrics r, int y) {
        if (r == null) return y;
        canvas.drawText(title + " (" + r.label + ")", 40, y, paint); y += 18;
        canvas.drawText("Screen-on drain: " + r.screenOnDrain + "%/h", 60, y, paint); y += 18;
        canvas.drawText("Idle drain: " + r.idleDrain + "%/h", 60, y, paint); y += 18;
        canvas.drawText("Avg charge: " + r.avgChargeMinutes + " min", 60, y, paint); y += 18;
        canvas.drawText("Max temp: " + r.maxTemp + "°C", 60, y, paint); y += 18;
        canvas.drawText("Health: " + r.avgHealth + "%", 60, y, paint); y += 18;
        canvas.drawText("Risk: " + r.riskScore + " (" + r.severity + ")", 60, y, paint); y += 18;
        return y;
    }
}
