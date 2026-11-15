import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;   // [NOVO]

class Buffer {
    static final int CAPACITY = 50;   // [NOVO] capacidade máxima do buffer
    static final int END = -1;        // [NOVO] sinal de fim (itens produzidos são de 0 a 99, então -1 nunca é um item real)

    private final List<Integer> data = new ArrayList<>();

    // [NOVO] semáforos (produtor-consumidor com buffer limitado)
    private final Semaphore emptySlots = new Semaphore(CAPACITY); // espaços livres: começa em 50
    private final Semaphore fullSlots = new Semaphore(0);         // itens disponíveis: começa em 0
    private final Semaphore mutex = new Semaphore(1);             // acesso exclusivo à lista

    // [ALTERADO] bloqueia se o buffer estiver cheio
    public void put(int value) throws InterruptedException {
        emptySlots.acquire();        // espera haver espaço livre
        mutex.acquire();
        data.add(value);
        System.out.println("Inserted: " + value + " | Buffer size: " + data.size());
        mutex.release();
        fullSlots.release();         // avisa: há um item disponível
    }

    // [ALTERADO] bloqueia se o buffer estiver vazio (não retorna mais -1 por estar vazio)
    public int remove() throws InterruptedException {
        fullSlots.acquire();         // espera haver um item
        mutex.acquire();
        int value = data.remove(0);
        System.out.println("Removed: " + value + " | Buffer size: " + data.size());
        mutex.release();
        emptySlots.release();        // avisa: sobrou um espaço livre
        return value;
    }
}
