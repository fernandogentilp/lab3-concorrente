class Consumer implements Runnable {   // [ALTERADO] agora é Runnable
    private final Buffer buffer;
    private final int sleepTime;
    private final int id;
    private final boolean evenConsumer;    // [NOVO] true = consome pares, false = consome ímpares

    public Consumer(int id, Buffer buffer, int sleepTime) {
        this.id = id;
        this.buffer = buffer;
        this.sleepTime = sleepTime;
        this.evenConsumer = (id % 2 == 0); // [NOVO] id par consome pares, id ímpar consome ímpares
    }

    @Override
    public void run() {                    // [NOVO] é isso que a thread executa
        process();
    }

    public void process() {
        String type = evenConsumer ? "even" : "odd";
        while (true) {
            try {
                int item = buffer.remove(evenConsumer);  // [ALTERADO] bloqueia até haver item válido (sem mais o -1)
                System.out.println("Consumer " + id + " (" + type + ") consumed item " + item);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;                                  // [ALTERADO] sai do laço ao ser interrompido
            }
        }
    }
}
