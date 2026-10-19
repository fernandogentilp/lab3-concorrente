class Producer implements Runnable {   // [ALTERADO] agora é Runnable (vira tarefa de thread)
    private final Buffer buffer;
    private final int maxItems;
    private final int sleepTime;
    private final int id;

    public Producer(int id, Buffer buffer, int maxItems, int sleepTime) {
        this.id = id;
        this.buffer = buffer;
        this.maxItems = maxItems;
        this.sleepTime = sleepTime;
    }

    @Override
    public void run() {                // [NOVO] é isso que a thread executa
        produce();
    }

    public void produce() {
        for (int i = 0; i < maxItems; i++) {
            try {
                Thread.sleep(sleepTime);
                int item = (int) (Math.random() * 100);
                System.out.println("Producer " + id + " produced item " + item);
                buffer.put(item);      // pode bloquear se o buffer estiver cheio
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;                // [ALTERADO] sai em vez de continuar o laço
            }
        }
    }
}
