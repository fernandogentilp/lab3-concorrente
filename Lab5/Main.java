public class Main {
    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Use: java Main <num_producers> <max_items_per_producer> <producing_time> <num_consumers> <consuming_time>");
            return;
        }

        int numProducers = Integer.parseInt(args[0]);
        int maxItemsPerProducer = Integer.parseInt(args[1]);
        int producingTime = Integer.parseInt(args[2]);
        int numConsumers = Integer.parseInt(args[3]);
        int consumingTime = Integer.parseInt(args[4]);

        // [NOVO] precisa de pelo menos 1 produtor e 1 consumidor (senão alguém esperaria para sempre)
        if (numProducers < 1 || numConsumers < 1) {
            System.out.println("num_producers e num_consumers devem ser no mínimo 1.");
            return;
        }

        Buffer buffer = new Buffer();

        // [ALTERADO] antes: producer.produce() / consumer.process() direto (serial).
        // Agora: cada produtor e cada consumidor roda na sua própria thread.
        Thread[] producers = new Thread[numProducers];
        Thread[] consumers = new Thread[numConsumers];

        for (int i = 1; i <= numProducers; i++) {
            Producer producer = new Producer(i, buffer, maxItemsPerProducer, producingTime);
            producers[i - 1] = new Thread(producer, "Producer-" + i);
            producers[i - 1].start();
        }

        for (int i = 1; i <= numConsumers; i++) {
            Consumer consumer = new Consumer(i, buffer, consumingTime);
            consumers[i - 1] = new Thread(consumer, "Consumer-" + i);
            consumers[i - 1].start();
        }

        // [NOVO] encerramento: espera todos os produtores terminarem...
        try {
            for (Thread t : producers) {
                t.join();
            }

            // ...e então manda um sinal de fim (END) para cada consumidor. Como o buffer é FIFO,
            // os sinais ficam atrás de todos os itens reais, que são consumidos antes.
            for (int i = 0; i < numConsumers; i++) {
                buffer.put(Buffer.END);
            }

            for (Thread t : consumers) {
                t.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
