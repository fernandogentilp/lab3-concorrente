import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;   // [NOVO]

public class FileIndexingPipeline {

    static final int BUFFER_CAPACITY = 50;   // [NOVO] capacidade máxima de cada buffer

    // Só a thread Indexador escreve aqui; a main só lê depois do join
    static Map<String, Map<String, Integer>> fileIndex = new HashMap<>();

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Uso: java FileIndexingPipeline <arquivo1.txt> <arquivo2.txt> ...");
            return;
        }

        Buffer<FileData> readBuffer = new Buffer<>(BUFFER_CAPACITY);    // [ALTERADO] agora tem capacidade
        Buffer<FileData> tokenBuffer = new Buffer<>(BUFFER_CAPACITY);   // [ALTERADO] agora tem capacidade

        // [ALTERADO] antes: um for chamava readFile, tokenize e index em sequência (serial).
        // Agora: uma thread por etapa, conectadas pelos buffers.
        Thread reader = new Thread(new ReaderTask(args, readBuffer), "Leitor");
        Thread tokenizer = new Thread(new TokenizerTask(readBuffer, tokenBuffer), "Tokenizador");
        Thread indexer = new Thread(new IndexerTask(tokenBuffer), "Indexador");

        reader.start();
        tokenizer.start();
        indexer.start();

        // [NOVO] a main espera o pipeline inteiro terminar (o null percorre as 3 etapas)
        try {
            reader.join();
            tokenizer.join();
            indexer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        System.out.println("fileIndex:");
        for (var word : fileIndex.keySet()) {
            System.out.println(word + " -> " + fileIndex.get(word));
        }
    }

    // ---------------------------------------------------------------
    // [NOVO] Tarefas das threads de cada etapa
    // ---------------------------------------------------------------

    // Etapa 1: lê os arquivos e insere no readBuffer; no fim insere null (EOF)
    static class ReaderTask implements Runnable {
        private final String[] paths;
        private final Buffer<FileData> readBuffer;

        ReaderTask(String[] paths, Buffer<FileData> readBuffer) {
            this.paths = paths;
            this.readBuffer = readBuffer;
        }

        @Override
        public void run() {
            try {
                for (String pathStr : paths) {
                    readFile(pathStr, readBuffer);
                }
                readBuffer.insert(null);   // sinaliza fim: não há mais arquivos
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Etapa 2: consome do readBuffer, tokeniza e insere no tokenBuffer; repassa o null
    static class TokenizerTask implements Runnable {
        private final Buffer<FileData> readBuffer;
        private final Buffer<FileData> tokenBuffer;

        TokenizerTask(Buffer<FileData> readBuffer, Buffer<FileData> tokenBuffer) {
            this.readBuffer = readBuffer;
            this.tokenBuffer = tokenBuffer;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    FileData fileData = readBuffer.remove();   // bloqueia se estiver vazio
                    if (fileData == null) {                     // EOF: repassa e termina
                        tokenBuffer.insert(null);
                        return;
                    }
                    tokenize(fileData, tokenBuffer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Etapa 3: consome do tokenBuffer e atualiza o índice; termina ao receber null
    static class IndexerTask implements Runnable {
        private final Buffer<FileData> tokenBuffer;

        IndexerTask(Buffer<FileData> tokenBuffer) {
            this.tokenBuffer = tokenBuffer;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    FileData fileData = tokenBuffer.remove();  // bloqueia se estiver vazio
                    if (fileData == null) {                     // EOF: fim do pipeline
                        return;
                    }
                    index(fileData);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---------------------------------------------------------------
    // Funções das etapas (mesma lógica do serial)
    // ---------------------------------------------------------------

    // [ALTERADO] insert pode bloquear (buffer cheio) -> declara InterruptedException
    public static boolean readFile(String pathStr, Buffer<FileData> readBuffer) throws InterruptedException {
        try {
            Path path = Paths.get(pathStr);
            String content = Files.readString(path);
            readBuffer.insert(new FileData(path.getFileName().toString(), content));
            return true;
        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo " + pathStr + ": " + e.getMessage());
            return false;
        }
    }

    // [ALTERADO] agora recebe o item já removido pela thread (a remoção e o null ficam no TokenizerTask)
    public static void tokenize(FileData fileData, Buffer<FileData> tokenBuffer) throws InterruptedException {
        String[] words = fileData.content.split("\\s+");
        String newContent = String.join(",", words);
        tokenBuffer.insert(new FileData(fileData.name, newContent));
    }

    // [ALTERADO] agora recebe o item já removido pela thread (a remoção e o null ficam no IndexerTask)
    public static void index(FileData fileData) {
        String[] words = fileData.content.split(",");
        for (String word : words) {
            fileIndex.putIfAbsent(word, new HashMap<>());
            Map<String, Integer> fileDatas = fileIndex.get(word);
            fileDatas.put(fileData.name, fileDatas.getOrDefault(fileData.name, 0) + 1);
        }
    }

    static class FileData {
        public final String name;
        public final String content;

        public FileData(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }

    // [ALTERADO] Buffer limitado e thread-safe com semáforos (produtor-consumidor).
    // Usa vetor circular (aceita null, que é o nosso sinal de fim) em vez da Queue ilimitada.
    static class Buffer<T> {
        private final Object[] items;
        private final int capacity;
        private int in = 0;    // próxima posição de inserção
        private int out = 0;   // próxima posição de remoção

        private final Semaphore emptySlots;                  // espaços livres (começa em capacity)
        private final Semaphore fullSlots = new Semaphore(0); // itens disponíveis (começa em 0)
        private final Semaphore mutex = new Semaphore(1);     // protege items, in e out

        public Buffer(int capacity) {
            this.capacity = capacity;
            this.items = new Object[capacity];
            this.emptySlots = new Semaphore(capacity);
        }

        public void insert(T item) throws InterruptedException {
            emptySlots.acquire();   // bloqueia se o buffer estiver cheio
            mutex.acquire();
            items[in] = item;
            in = (in + 1) % capacity;
            mutex.release();
            fullSlots.release();    // avisa que há um item disponível
        }

        @SuppressWarnings("unchecked")
        public T remove() throws InterruptedException {
            fullSlots.acquire();    // bloqueia se o buffer estiver vazio
            mutex.acquire();
            T item = (T) items[out];
            items[out] = null;
            out = (out + 1) % capacity;
            mutex.release();
            emptySlots.release();   // libera uma posição
            return item;
        }
    }
}
