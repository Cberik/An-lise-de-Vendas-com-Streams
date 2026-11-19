# Atividade 4 — Análise de Vendas com Streams

Projeto Java (Maven) que lê um arquivo CSV com mais de 5 mil vendas (2010 a 2014) e responde a perguntas sobre esses dados usando **Streams**, **Collectors**, **Optional**, **PriorityQueue** e **Gatherers**.

A classe `LeitorDeVendas` carrega o arquivo uma vez e expõe 10 consultas, cada uma coberta por um teste em `LeitorDeVendasTest`.

## Requisitos

| Ferramenta | Versão |
|------------|--------|
| JDK        | **25** ou superior |
| Maven      | 3.9.11 (não precisa instalar, o projeto traz o Maven Wrapper) |

> O tópico 7 usa `Gatherers`, que faz parte da API padrão a partir do Java 24. Com um JDK mais antigo o projeto não compila.

## Como executar

Na raiz do projeto (a pasta onde está o `pom.xml`):

```bash
# Linux / macOS
./mvnw test

# Windows
mvnw.cmd test
```

Para rodar apenas uma classe de teste ou um tópico específico:

```bash
./mvnw test -Dtest=LeitorDeVendasTest
./mvnw test -Dtest=LocalDateConverterTest
./mvnw test -Dtest=LeitorDeVendasTest#assert_resumoDeVendasConcluidas
```

## Estrutura do projeto

```
atividade4/
├── pom.xml
├── mvnw / mvnw.cmd                     # Maven Wrapper
└── src/
    ├── main/
    │   ├── java/br/edu/utfpr/
    │   │   ├── LeitorDeVendas.java         # leitura do CSV e as 10 consultas
    │   │   ├── VendaCsv.java               # mapeamento das colunas do CSV
    │   │   ├── LocalDateConverter.java     # converte "dd/MM/yyyy" em LocalDate
    │   │   ├── FormaPagamentoConverter.java
    │   │   ├── StatusConverter.java
    │   │   └── model/
    │   │       ├── Venda.java              # record com os dados de uma venda
    │   │       ├── FormaPagamento.java     # enum
    │   │       ├── Status.java             # enum (CONCLUIDA / CANCELADA)
    │   │       ├── RankingVendedor.java    # record (vendedor, faturamento)
    │   │       └── ResumoVendas.java       # record (total, media, quantidade)
    │   └── resources/vendas.csv
    └── test/
        ├── java/br/edu/utfpr/
        │   ├── LeitorDeVendasTest.java     # 10 testes, um por tópico
        │   └── LocalDateConverterTest.java
        └── resources/vendas.csv
```

## Como os dados são lidos

1. O `LeitorDeVendas` recebe o nome do arquivo no construtor (os testes usam `"vendas.csv"`) e o procura no classpath. Se não encontrar, lança `IllegalStateException`.
2. O OpenCSV lê o arquivo (UTF-8, separador `;`) e preenche objetos `VendaCsv` a partir dos nomes das colunas.
3. Conversores customizados transformam o texto em tipos do domínio:
   - `LocalDateConverter`: datas no formato `dd/MM/yyyy`.
   - `FormaPagamentoConverter`: "Cartão de Crédito", "Cartão de Débito", "Boleto Bancário" e "Transferência Eletrônica" viram o enum `FormaPagamento`.
   - `StatusConverter`: "Concluída" e "Cancelada" viram o enum `Status`.
4. Cada `VendaCsv` vira um `Venda` imutável (`record`), e a lista resultante fica guardada no leitor.

### Formato do CSV

```
num_venda;data_venda;data_entrega;uf;regiao;gestor;vendedor;departamento;forma_pagamento;valor_venda;status
4085;01/01/2010;06/01/2010;SP;Sudeste;Luiz Miranda;Adriana Gomes;Casa e Construção;Cartão de Débito;4546.71;Concluída
```

O separador de campos é `;`, e o decimal de `valor_venda` é o ponto.

## Consultas implementadas

| # | Método | O que faz | Como |
|---|--------|-----------|------|
| 1 | `vendasConcluidasAcimaDoValorNaRegiao(valorMinimo, regiao)` | Vendas concluídas da região com valor maior que o mínimo | Três `Predicate` combinados com `and` |
| 2 | `vendaDeMaiorValorNaRegiao(regiao)` | Venda de maior valor da região; `Optional` vazio se a região não existir | `filter` + `max`, devolvendo `Optional` direto |
| 3 | `topNVendasPorValor(n)` | As `n` vendas de maior valor, da maior para a menor | `PriorityQueue` (min-heap) com tamanho limitado a `n` |
| 4 | `top3VendedoresPorFaturamento()` | Três vendedores com maior faturamento em vendas concluídas | `groupingBy` + `reducing(BigDecimal::add)`, ordenação e `limit(3)` |
| 5 | `valorTotalDeVendasConcluidasPorRegiao()` | Soma das vendas concluídas por região | `groupingBy` + `reducing` |
| 6 | `resumoDeVendasConcluidas()` | Total, média (2 casas, `HALF_UP`) e quantidade das vendas concluídas | `Collector.of` com a classe interna `AcumuladorDeResumo` |
| 7 | `faturamentoAcumuladoPorVendedorEMes(vendedor, mes)` | Faturamento acumulado, em ordem de data, das vendas concluídas de um vendedor em um mês | `Gatherers.scan` |
| 8 | `formaPagamentoComMaisCancelamentos()` | Forma de pagamento com mais vendas canceladas | `groupingBy` + `counting` + `max` |
| 9 | `diasEntrePrimeiraEUltimaVendaCancelada()` | Dias entre a primeira e a última venda cancelada | `Collectors.teeing` com `minBy` e `maxBy` |
| 10 | `quantidadeDeVendasConcluidasPorFormaDePagamentoAgrupadasPorAno()` | Contagem de vendas concluídas por ano e por forma de pagamento | `groupingBy` aninhado |

Valores monetários usam `BigDecimal` para evitar erros de arredondamento de ponto flutuante.

## Tecnologias

- Java 25
- [OpenCSV](https://opencsv.sourceforge.net/) 5.12.0, para leitura do CSV
- [JUnit Jupiter](https://junit.org/junit5/) 5.11.4 e [AssertJ](https://assertj.github.io/doc/) 3.27.7, para testes
- Maven Compiler Plugin 3.15.0 e Surefire 3.5.6
