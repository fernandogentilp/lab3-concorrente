import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

public class DnaConcurrentMain {

    // [NOVO] Tarefa de cada thread: processa os arquivos id, id+numThreads, id+2*numThreads...
    private static class DnaWorker implements Runnable {
        private final File[] files;
        private final String pattern;
        private final int id;
        private final int numThreads;
        private final long[] partialResults;   // cada thread escreve só na posição [id]
        private final IOException[] errors;    // cada thread escreve só na posição [id]

        DnaWorker(File[] files, String pattern, int id, int numThreads,
                  long[] partialResults, IOException[] errors) {
            this.files = files;
            this.pattern = pattern;
            this.id = id;
            this.numThreads = numThreads;
            this.partialResults = partialResults;
            this.errors = errors;
        }

        @Override
        public void run() {
            long local = 0; // contador local: ninguém mais mexe nele
            try {
                for (int i = id; i < files.length; i += numThreads) {
                    local += countInFile(files[i], pattern);
                }
            } catch (IOException e) {
                errors[id] = e; // run() não pode lançar exceção, então guarda para a main
            }
            partialResults[id] = local;
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: java DnaConcurrentMain DIRETORIO_ARQUIVOS PADRAO");   // [ALTERADO] estava DnaSerialMain
            System.err.println("Exemplo: java DnaConcurrentMain dna_inputs CGTAA");        // [ALTERADO] estava DnaSerialMain
            System.exit(1);
        }

        String dirName = args[0];
        String pattern = args[1];

        File dir = new File(dirName);
        if (!dir.isDirectory()) {
            System.err.println("Caminho não é um diretório: " + dirName);
            System.exit(2);
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            System.err.println("Nenhum arquivo .txt encontrado em: " + dirName);
            System.exit(3);
        }

        // [NOVO] número fixo de threads: no máximo o número de núcleos (e nunca mais que o número de arquivos)
        int numThreads = Math.min(files.length, Runtime.getRuntime().availableProcessors());

        // [NOVO] resultados parciais: uma posição por thread, sem disputa
        long[] partialResults = new long[numThreads];
        IOException[] errors = new IOException[numThreads];
        Thread[] threads = new Thread[numThreads];

        // [NOVO] cria e inicia as threads
        for (int i = 0; i < numThreads; i++) {
            DnaWorker worker = new DnaWorker(files, pattern, i, numThreads, partialResults, errors);
            threads[i] = new Thread(worker, "Dna-Worker-" + i);
            threads[i].start();
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

        // [ALTERADO] em vez do for que chamava countInFile, soma os parciais (só depois do join)
        long total = 0;
        for (int i = 0; i < numThreads; i++) {
            if (errors[i] != null) {
                System.err.println("Erro ao ler arquivos: " + errors[i].getMessage());
                System.exit(4);
            }
            total += partialResults[i];
        }

        System.out.println("Sequência " + pattern + " foi encontrada " + total + " vezes.");
    }


    public static long countInFile(File file, String pattern) throws IOException {
        long total = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    total += countInSequence(line, pattern);
                }
            }
        }
        return total;
    }

    public static long countInSequence(String sequence, String pattern) {
        if (sequence == null || pattern == null) {
            return 0;
        }
        int n = sequence.length();
        int m = pattern.length();
        if (m == 0 || n < m) {
            return 0;
        }
        long count = 0;
        for (int i = 0; i <= n - m; i++) {
            if (sequence.regionMatches(false, i, pattern, 0, m)) {
                count++;
            }
        }
        return count;
    }

}
