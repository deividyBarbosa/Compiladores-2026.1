package dplusplus;

import java.io.FileReader;
import java.io.PushbackReader;
import java.util.List;

import dplusplus.lexer.Lexer;
import dplusplus.lexer.LexerException;
import dplusplus.node.Start;
import dplusplus.parser.Parser;
import dplusplus.parser.ParserException;
import dplusplus.semantico.AnalisadorSemantico;
import dplusplus.semantico.ColetorDeClasses;
import dplusplus.semantico.GerenciadorErros;
import dplusplus.semantico.InfoClasse;
import dplusplus.semantico.Simbolo;
import dplusplus.semantico.TabelaClasses;

/**
 * Driver de linha de comando da Etapa 5: lê um arquivo .dpp, executa o
 * analisador léxico (Etapa 2) e o analisador sintático/sintático-abstrato
 * (Etapas 3/4) do SableCC e, se a árvore for construída com sucesso, roda
 * a análise semântica (Etapa 5): primeiro {@link ColetorDeClasses} (monta a
 * tabela de classes/herança) e depois {@link AnalisadorSemantico} (o
 * visitor que percorre a árvore checando escopo, inicialização e tipos).
 *
 * Uso: java dplusplus.Main caminho/para/arquivo.dpp [--tabela]
 * ("--tabela" imprime, ao final, um resumo da tabela de classes montada.)
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java dplusplus.Main <arquivo.dpp> [--tabela]");
            return;
        }

        String arquivo = args[0];
        boolean imprimirTabela = args.length > 1 && "--tabela".equals(args[1]);

        System.out.println("=================================================");
        System.out.println("Compilando: " + arquivo);
        System.out.println("=================================================");

        Start arvore;
        try (PushbackReader leitor = new PushbackReader(new FileReader(arquivo), 1024)) {
            Parser parser = new Parser(new Lexer(leitor));
            arvore = parser.parse();
            System.out.println("Análise léxica e sintática concluídas sem erros.");
        } catch (LexerException e) {
            System.out.println("Erro léxico: " + e.getMessage());
            return;
        } catch (ParserException e) {
            System.out.println("Erro sintático: " + e.getMessage());
            return;
        } catch (Exception e) {
            System.out.println("Erro ao ler/compilar o arquivo: " + e.getMessage());
            return;
        }

        GerenciadorErros erros = new GerenciadorErros();

        TabelaClasses tabelaClasses = new ColetorDeClasses(erros).coletar(arvore);
        arvore.apply(new AnalisadorSemantico(tabelaClasses, erros));

        erros.imprimirRelatorio();

        if (imprimirTabela) {
            imprimirTabelaDeClasses(tabelaClasses);
        }

        if (erros.temErros()) {
            System.exit(1);
        }
    }

    private static void imprimirTabelaDeClasses(TabelaClasses tabelaClasses) {
        System.out.println("-------------------------------------------------");
        System.out.println("Tabela de classes (resultado da Etapa 5):");
        for (InfoClasse classe : tabelaClasses.todas()) {
            String pai = classe.getPai() != null ? classe.getPai().getNome() : "-";
            System.out.println("  family " + classe.getNome() + " (mãe: " + pai + ")"
                + (classe.isAbstrata() ? " [abstrata]" : "") + (classe.isPredefinida() ? " [pré-definida]" : ""));
            List<Simbolo> membros = classe.getTabelaMembros().todosOsSimbolos();
            membros.sort((a, b) -> a.getNome().compareTo(b.getNome()));
            for (Simbolo membro : membros) {
                String origem = classe.getNome().equals(membro.getClasseDeOrigem())
                    ? "" : " (herdado de " + membro.getClasseDeOrigem() + ")";
                System.out.println("      " + membro.getCategoria() + " " + membro.assinatura() + origem);
            }
        }
        System.out.println("-------------------------------------------------");
    }
}
