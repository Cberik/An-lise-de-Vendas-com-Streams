package br.edu.utfpr;

import br.edu.utfpr.model.FormaPagamento;
import br.edu.utfpr.model.RankingVendedor;
import br.edu.utfpr.model.ResumoVendas;
import br.edu.utfpr.model.Venda;
import com.opencsv.bean.CsvToBeanBuilder;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;

public class LeitorDeVendas {

    private final List<Venda> vendas;

    public LeitorDeVendas(String salesFile) {

        final var stream = getClass().getClassLoader().getResourceAsStream(salesFile);

        if (stream == null) {
            throw new IllegalStateException("Arquivo não encontrado");
        }
        final var builder = new CsvToBeanBuilder<VendaCsv>(new InputStreamReader(stream, StandardCharsets.UTF_8));

        vendas = builder
                .withType(VendaCsv.class)
                .withSeparator(';')
                .build()
                .parse()
                .stream()
                .map(VendaCsv::toVenda)
                .toList();
    }

    public List<Venda> vendasConcluidasAcimaDoValorNaRegiao(BigDecimal valorMinimo, String regiao) {

        Predicate<Venda> concluida = Venda::isConcluida;

        Predicate<Venda> valorMaior
                = v -> v.valor().compareTo(valorMinimo) > 0;

        Predicate<Venda> mesmaRegiao
                = v -> v.regiao().equals(regiao);

        return vendas.stream()
                .filter(concluida
                        .and(valorMaior)
                        .and(mesmaRegiao))
                .toList();
    }

    public Optional<Venda> vendaDeMaiorValorNaRegiao(String regiao) {

        return vendas.stream()
                .filter(v -> v.regiao().equals(regiao))
                .max(Comparator.comparing(Venda::valor));

    }

    public List<Venda> topNVendasPorValor(int n) {

        Queue<Venda> fila = new PriorityQueue<>(
                Comparator.comparing(Venda::valor));

        for (Venda venda : vendas) {

            if (fila.size() < n) {
                fila.offer(venda);
            } else if (venda.valor().compareTo(fila.peek().valor()) > 0) {
                fila.poll();
                fila.offer(venda);
            }
        }
        return fila.stream()
                .sorted(Comparator.comparing(Venda::valor).reversed())
                .toList();
    }

    public List<RankingVendedor> top3VendedoresPorFaturamento() {
        return vendas.stream()
                .filter(Venda::isConcluida)
                .collect(Collectors.groupingBy(
                        Venda::vendedor,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Venda::valor,
                                BigDecimal::add)))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(3)
                .map(entry -> new RankingVendedor(
                entry.getKey(),
                entry.getValue()))
                .toList();
    }

    public Map<String, BigDecimal> valorTotalDeVendasConcluidasPorRegiao() {

        return vendas.stream()
                .filter(Venda::isConcluida)
                .collect(Collectors.groupingBy(
                        Venda::regiao,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Venda::valor,
                                BigDecimal::add
                        )
                ));
    }

    public ResumoVendas resumoDeVendasConcluidas() {

        return vendas.stream()
                .filter(Venda::isConcluida)
                .collect(Collector.of(
                        AcumuladorDeResumo::new,
                        (acumulador, venda) -> acumulador.somar(venda.valor()),
                        AcumuladorDeResumo::combinar,
                        AcumuladorDeResumo::finalizar
                ));
    }

    public List<BigDecimal> faturamentoAcumuladoPorVendedorEMes(String vendedor, YearMonth mes) {

        return vendas.stream()
                .filter(Venda::isConcluida)
                .filter(v -> v.vendedor().equals(vendedor))
                .filter(v -> YearMonth.from(v.dataVenda()).equals(mes))
                .sorted(Comparator.comparing(Venda::dataVenda))
                .map(Venda::valor)
                .gather(Gatherers.scan(
                        () -> BigDecimal.ZERO,
                        (acumulado, valor) -> acumulado.add(valor)
                ))
                .toList();

    }

    public FormaPagamento formaPagamentoComMaisCancelamentos() {

        return vendas.stream()
                .filter(Venda::isCancelada)
                .collect(Collectors.groupingBy(
                        Venda::formaPagamento,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public long diasEntrePrimeiraEUltimaVendaCancelada() {
        return vendas.stream()
                .filter(Venda::isCancelada)
                .collect(Collectors.teeing(
                        Collectors.minBy(
                                Comparator.comparing(Venda::dataVenda)
                        ),
                        Collectors.maxBy(
                                Comparator.comparing(Venda::dataVenda)
                        ),
                        (primeira, ultima) -> ChronoUnit.DAYS.between(
                                primeira.get().dataVenda(),
                                ultima.get().dataVenda()
                        )
                ));
    }

    public Map<Integer, Map<FormaPagamento, Long>> quantidadeDeVendasConcluidasPorFormaDePagamentoAgrupadasPorAno() {
        return vendas.stream()
                .filter(Venda::isConcluida)
                .collect(Collectors.groupingBy(
                        venda -> venda.dataVenda().getYear(),
                        Collectors.groupingBy(
                                Venda::formaPagamento,
                                Collectors.counting()
                        )
                ));
    }

    private static final class AcumuladorDeResumo {

        private BigDecimal total = BigDecimal.ZERO;
        private long quantidade = 0;

        private void somar(BigDecimal valor) {
            total = total.add(valor);
            quantidade++;
        }

        private AcumuladorDeResumo combinar(AcumuladorDeResumo outro) {
            total = total.add(outro.total);
            quantidade += outro.quantidade;
            return this;
        }

        private ResumoVendas finalizar() {
            final var media = quantidade == 0
                    ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
            return new ResumoVendas(total, media, quantidade);
        }
    }
}
