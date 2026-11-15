class Consumer implements Runnable {   // [ALTERADO] agora é Runnable
    private final Buffer buffer;
    private final int sleepTime;
    private final int id;

    public Consumer(int id, Buffer buffer, int sleepTime) {
        this.id = id;
        this.buffer = buffer;
        this.sleepTime = sleepTime;
    }

    @Override
    public void run() {                // [NOVO] é isso que a thread executa
        process();
    }

    public void process() {
        while (true) {
            try {
                int item = buffer.remove();           // [ALTERADO] bloqueia se o buffer estiver vazio
                if (item == Buffer.END) break;        // [ALTERADO] só termina ao receber o sinal de fim
                System.out.println("Consumer " + id + " consumed item " + item);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;                               // [ALTERADO] sai em vez de continuar o laço
            }
        }
    }
}
