package dplusplus.semantico;

import java.util.ArrayList;
import java.util.List;

/**
 * Coleciona os erros (e avisos) encontrados durante a análise semântica.
 * Ao invés de abortar no primeiro problema, o analisador continua a
 * percorrer a árvore e acumula todas as mensagens aqui, para que o
 * desenvolvedor veja de uma vez só tudo o que precisa corrigir — no mesmo
 * espírito das etapas léxica e sintática do projeto.
 */
public class GerenciadorErros {

    public static final class Mensagem {
        public final int linha;
        public final int coluna;
        public final String texto;

        Mensagem(int linha, int coluna, String texto) {
            this.linha = linha;
            this.coluna = coluna;
            this.texto = texto;
        }

        @Override
        public String toString() {
            if (linha <= 0) {
                return "[Erro semântico] " + texto;
            }
            return "[Erro semântico] Linha " + linha + ", coluna " + coluna + ": " + texto;
        }
    }

    private final List<Mensagem> erros = new ArrayList<>();
    private final List<Mensagem> avisos = new ArrayList<>();

    public void erro(int linha, int coluna, String texto) {
        erros.add(new Mensagem(linha, coluna, texto));
    }

    public void erro(dplusplus.node.Token token, String texto) {
        if (token == null) {
            erro(0, 0, texto);
        } else {
            erro(token.getLine(), token.getPos(), texto);
        }
    }

    public void aviso(int linha, int coluna, String texto) {
        avisos.add(new Mensagem(linha, coluna, texto));
    }

    public boolean temErros() {
        return !erros.isEmpty();
    }

    public List<Mensagem> getErros() { return erros; }
    public List<Mensagem> getAvisos() { return avisos; }

    public void imprimirRelatorio() {
        System.out.println("-------------------------------------------------");
        if (erros.isEmpty()) {
            System.out.println("Análise semântica concluída sem erros.");
        } else {
            System.out.println("Análise semântica encontrou " + erros.size()
                + (erros.size() == 1 ? " erro:" : " erros:"));
            List<Mensagem> ordenadas = new ArrayList<>(erros);
            ordenadas.sort((a, b) -> {
                if (a.linha != b.linha) return Integer.compare(a.linha, b.linha);
                return Integer.compare(a.coluna, b.coluna);
            });
            for (Mensagem m : ordenadas) {
                System.out.println(m);
            }
        }
        if (!avisos.isEmpty()) {
            System.out.println("-------------------------------------------------");
            System.out.println(avisos.size() + (avisos.size() == 1 ? " aviso:" : " avisos:"));
            for (Mensagem m : avisos) {
                System.out.println(m);
            }
        }
        System.out.println("-------------------------------------------------");
    }
}
