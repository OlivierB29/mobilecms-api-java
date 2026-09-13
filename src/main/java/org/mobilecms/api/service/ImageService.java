package org.mobilecms.api.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ObjectNode;

import net.coobird.thumbnailator.Thumbnails;

@Service
public class ImageService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpeg", "jpg", "png", "gif");

    private final JsonFiles jsonFiles;

    public ImageService(JsonFiles jsonFiles) {
        this.jsonFiles = jsonFiles;
    }

    public boolean isImage(Path file) {
        String extension = extension(file);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            return false;
        }
        try {
            return ImageIO.read(file.toFile()) != null;
        } catch (IOException e) {
            return false;
        }
    }

    public ObjectNode imageInfo(Path file) {
        ObjectNode result = jsonFiles.object();
        result.put("mimetype", mimeType(file));
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image != null) {
                result.put("width", String.valueOf(image.getWidth()));
                result.put("height", String.valueOf(image.getHeight()));
            }
        } catch (IOException ignored) {
            // match PHP: still return mime and url
        }
        result.put("url", file.getFileName().toString());
        return result;
    }

    public ObjectNode pdfInfo(Path file) {
        ObjectNode result = jsonFiles.object();
        result.put("mimetype", mimeType(file));
        result.put("url", file.getFileName().toString());
        return result;
    }

    public List<ObjectNode> multipleResize(Path file, Path dir, List<Integer> sizes, int quality) {
        List<ObjectNode> result = new ArrayList<>();
        String fileName = stripExtension(file.getFileName().toString());
        String extension = extension(file);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (int width : sizes) {
            Path resized = dir.resolve(fileName + "-" + width + "." + extension);
            ObjectNode thumb = resize(file, resized, width, quality);
            if (thumb != null) {
                result.add(thumb);
            }
        }
        return result;
    }

    public List<ObjectNode> multiplePdfResize(Path file, Path dir, List<Integer> sizes, int quality) {
        List<ObjectNode> result = new ArrayList<>();
        String fileName = stripExtension(file.getFileName().toString());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (int width : sizes) {
            Path resized = dir.resolve(fileName + "-" + width + ".jpg");
            ObjectNode thumb = resizePdf(file, resized, width, quality);
            if (thumb != null) {
                result.add(thumb);
            }
        }
        return result;
    }

    public ObjectNode resize(Path fileName, Path thumbFile, int width, int quality) {
        try {
            BufferedImage original = ImageIO.read(fileName.toFile());
            if (original == null || original.getWidth() <= width) {
                return null;
            }
            String mime = mimeType(fileName);
            double ratio = (double) original.getWidth() / original.getHeight();
            int height = (int) Math.round(width / ratio);
            Files.createDirectories(thumbFile.getParent());
            if ("image/jpeg".equals(mime) || "image/jpg".equals(mime)) {
                Thumbnails.of(original)
                        .size(width, height)
                        .outputQuality(Math.max(0.01, quality / 100.0))
                        .outputFormat("jpg")
                        .toFile(thumbFile.toFile());
            } else if ("image/png".equals(mime)) {
                BufferedImage destination = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = destination.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(original, 0, 0, width, height, null);
                graphics.dispose();
                ImageIO.write(destination, "png", thumbFile.toFile());
            } else {
                return null;
            }
            ObjectNode result = jsonFiles.object();
            result.put("width", String.valueOf(width));
            result.put("height", String.valueOf(height));
            result.put("url", thumbFile.getFileName().toString());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public ObjectNode resizePdf(Path source, Path target, int width, int quality) {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 144);
            double ratio = (double) image.getWidth() / image.getHeight();
            int height = (int) Math.round(width / ratio);
            Files.createDirectories(target.getParent());
            BufferedImage scaled = Thumbnails.of(image).size(width, height).asBufferedImage();
            writeJpeg(scaled, target, quality);
            ObjectNode result = jsonFiles.object();
            result.put("width", String.valueOf(width));
            result.put("height", String.valueOf(height));
            result.put("url", target.getFileName().toString());
            return result;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeJpeg(BufferedImage image, Path target, int quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", target.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0.01f, quality / 100f));
        }
        try (FileImageOutputStream output = new FileImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public String mimeType(Path file) {
        try {
            String probed = Files.probeContentType(file);
            if (probed != null) {
                return probed;
            }
        } catch (IOException ignored) {
            // fall through
        }
        return switch (extension(file)) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            default -> "application/octet-stream";
        };
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
