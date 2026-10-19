import java.util.ArrayList;
import java.util.List;

class Buffer {
    private static final int CAPACITY = 100;   // [NOVO] capacidade máxima do buffer
    private final List<Integer> data = new ArrayList<>();

    // [ALTERADO] synchronized (exclusão mútua) + espera enquanto o buffer estiver cheio
    public synchronized void put(int value) throws InterruptedException {
        while (data.size() == CAPACITY) {
            wait();                            // [NOVO] buffer cheio: produtor espera
        }
        data.add(value);
        System.out.println("Inserted: " + value + " | Buffer size: " + data.size());
        notifyAll();                           // [NOVO] acorda consumidores que esperam item
    }

    // [ALTERADO] consumidor condicional: só retorna item par (even = true) ou ímpar (even = false)
    public synchronized int remove(boolean even) throws InterruptedException {
        while (true) {
            // [NOVO] dá uma volta no buffer procurando um item que atenda à condição
            int attempts = data.size();
            for (int i = 0; i < attempts; i++) {
                int value = data.remove(0);
                if ((value % 2 == 0) == even) {
                    System.out.println("Removed: " + value + " | Buffer size: " + data.size());
                    notifyAll();               // [NOVO] liberou espaço: acorda produtores
                    return value;
                }
                data.add(value);               // [NOVO] não atende à condição: reinsere no final
            }
            wait();                            // [NOVO] vazio ou sem item válido: espera novo item
        }
    }
}
