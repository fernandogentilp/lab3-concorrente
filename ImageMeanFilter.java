import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * This class provides functionality to apply a mean filter to an image.
 * The mean filter is used to smooth images by averaging the pixel values
 * in a neighborhood defined by a kernel size.
 *
 * Versão CONCORRENTE: a imagem é dividida em faixas de linhas e cada thread
 * processa uma faixa. Ao final, a main imprime quantos pixels foram alterados
 * pelo filtro e quantos permaneceram iguais.
 *
 * <p>Author (código base): temmanuel@comptuacao.ufcg.edu.br</p>
 */
public class ImageMeanFilter {

    // [NOVO] Tarefa de cada thread: filtra as linhas [startY, endY) da imagem
    private static class FilterWorker implements Runnable {
        private final BufferedImage original; // compartilhada, SOMENTE leitura
        private final int[] output;           // compartilhada: cada thread escreve só nas suas linhas
        private final int width;
        private final int kernelSize;
        private final int startY;
        private final int endY;

        // [NOVO] contadores locais da thread (ninguém mais mexe neles durante a execução)
        private long changed = 0;
        private long unchanged = 0;

        FilterWorker(BufferedImage original, int[] output, int width, int kernelSize,
                     int startY, int endY) {
            this.original = original;
            this.output = output;
            this.width = width;
            this.kernelSize = kernelSize;
            this.startY = startY;
            this.endY = endY;
        }

        @Override
        public void run() {
            for (int y = startY; y < endY; y++) {
                for (int x = 0; x < width; x++) {
                    // Calculate neighborhood average (índice 3 = pixel original, sem alpha)
                    int[] avgColor = calculateNeighborhoodAverage(original, x, y, kernelSize);

                    int newRgb = (avgColor[0] << 16) | (avgColor[1] << 8) | avgColor[2];
                    output[y * width + x] = newRgb;

                    // [NOVO] Etapa 2: o valor do pixel mudou ou permaneceu igual?
                    if (newRgb != avgColor[3]) {
                        changed++;
                    } else {
                        unchanged++;
                    }
                }
            }
        }
    }

    /**
     * Applies mean filter to an image
     *
     * @param inputPath  Path to input image
     * @param outputPath Path to output image
     * @param kernelSize Size of mean kernel
     * @param numThreads Number of threads (minimum 2)
     * @throws IOException If there is an error reading/writing
     */
    public static void applyMeanFilter(String inputPath, String outputPath, int kernelSize,
                                       int numThreads) throws IOException {
        // Load image
        BufferedImage originalImage = ImageIO.read(new File(inputPath));

        // Image processing
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // [ALTERADO] resultado vai para um vetor (em vez de filteredImage.setRGB dentro das
        // threads), assim nenhuma thread escreve no BufferedImage; ele é montado só pela main
        int[] output = new int[width * height];

        // [NOVO] nunca mais threads do que linhas
        int nThreads = Math.min(numThreads, height);

        // [NOVO] divide as linhas em faixas contíguas (as primeiras faixas pegam o resto)
        FilterWorker[] workers = new FilterWorker[nThreads];
        Thread[] threads = new Thread[nThreads];
        int baseRows = height / nThreads;
        int extraRows = height % nThreads;
        int startY = 0;
        for (int i = 0; i < nThreads; i++) {
            int rows = baseRows + (i < extraRows ? 1 : 0);
            workers[i] = new FilterWorker(originalImage, output, width, kernelSize,
                                          startY, startY + rows);
            threads[i] = new Thread(workers[i], "Filter-" + i);
            threads[i].start();
            startY += rows;
        }

        // [NOVO] main espera todas as threads terminarem
        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Create result image (só a main escreve nele, depois do join)
        BufferedImage filteredImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        filteredImage.setRGB(0, 0, width, height, output, 0, width);

        // [NOVO] soma os contadores locais (só depois do join, sem race)
        long totalChanged = 0;
        long totalUnchanged = 0;
        for (FilterWorker w : workers) {
            totalChanged += w.changed;
            totalUnchanged += w.unchanged;
        }

        // Save filtered image
        ImageIO.write(filteredImage, "jpg", new File(outputPath));

        System.out.println("Threads utilizadas: " + nThreads);
        System.out.println("Pixels alterados pelo filtro: " + totalChanged);
        System.out.println("Pixels que permaneceram iguais: " + totalUnchanged);
        System.out.println("Total de pixels: " + (totalChanged + totalUnchanged));
    }

    /**
     * Calculates average colors in a pixel's neighborhood
     *
     * @param image      Source image
     * @param centerX    X coordinate of center pixel
     * @param centerY    Y coordinate of center pixel
     * @param kernelSize Kernel size
     * @return Array with R, G, B averages and, at index 3, the original RGB of the center pixel
     */
    private static int[] calculateNeighborhoodAverage(BufferedImage image, int centerX, int centerY, int kernelSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        int pad = kernelSize / 2;

        // Arrays for color sums
        long redSum = 0, greenSum = 0, blueSum = 0;
        int pixelCount = 0;
        int centerRgb = 0; // [NOVO] valor original do pixel central (para a Etapa 2)

        // Process neighborhood
        for (int dy = -pad; dy <= pad; dy++) {
            for (int dx = -pad; dx <= pad; dx++) {
                int x = centerX + dx;
                int y = centerY + dy;

                // Check image bounds
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    // Get pixel color
                    int rgb = image.getRGB(x, y);

                    // [NOVO] guarda o pixel central (sem o canal alpha)
                    if (dx == 0 && dy == 0) {
                        centerRgb = rgb & 0xFFFFFF;
                    }

                    // Extract color components
                    int red = (rgb >> 16) & 0xFF;
                    int green = (rgb >> 8) & 0xFF;
                    int blue = rgb & 0xFF;

                    // Sum colors
                    redSum += red;
                    greenSum += green;
                    blueSum += blue;
                    pixelCount++;
                }
            }
        }

        // Calculate average
        return new int[] {
            (int)(redSum / pixelCount),
            (int)(greenSum / pixelCount),
            (int)(blueSum / pixelCount),
            centerRgb
        };
    }

    /**
     * Main method
     *
     * Usage: java ImageMeanFilter <input_file> <num_threads>
     *
     * Arguments:
     *   input_file  - Path to the input image file (JPG, PNG)
     *   num_threads - Number of threads to use (minimum 2)
     *
     * The program generates "filtered_output.jpg" using a 7x7 mean filter kernel
     * and prints how many pixels were changed / stayed the same.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java ImageMeanFilter <input_file> <num_threads>");
            System.exit(1);
        }

        String inputFile = args[0];

        // [NOVO] valida o número de threads (mínimo 2)
        int numThreads;
        try {
            numThreads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("num_threads deve ser um número inteiro.");
            System.exit(1);
            return;
        }
        if (numThreads < 2) {
            System.err.println("num_threads deve ser no mínimo 2.");
            System.exit(1);
        }

        try {
            applyMeanFilter(inputFile, "filtered_output.jpg", 7, numThreads);
        } catch (IOException e) {
            System.err.println("Error processing image: " + e.getMessage());
        }
    }
}
