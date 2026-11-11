f = "app/src/main/java/com/yiwalo/tunnel/TestConfigsView.java"
s = open(f, encoding="utf-8").read()
L = s.split("\n")

a = next(i for i, l in enumerate(L) if 'o.write(("GET /generate_204' in l)
b = next(i for i, l in enumerate(L) if i > a and "return (System.nanoTime() - t0) / 1000000L;" in l)
nouveau = r'''            byte[] req = ("GET /generate_204 HTTP/1.1\r\nHost: cp.cloudflare.com\r\n"
                    + "Connection: keep-alive\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            // 1re requete : etablit la connexion vers le serveur (TCP/TLS/WS), non comptee
            o.write(req);
            o.flush();
            lireEntetes(in);
            // 2e requete sur la meme connexion : latence reelle (RTT)
            long t1 = System.nanoTime();
            o.write(req);
            o.flush();
            byte[] b = new byte[12];
            lireTout(in, b);
            String st = new String(b, java.nio.charset.StandardCharsets.US_ASCII);
            if (!st.startsWith("HTTP/1.")) throw new IOException("réponse inattendue du serveur");
            return (System.nanoTime() - t1) / 1000000L;'''.split("\n")
L[a:b + 1] = nouveau

c = next(i for i, l in enumerate(L) if "Test réel : lance le moteur Xray" in l)
helper = r'''    /** Lit jusqu'a la fin des entetes HTTP (CRLF CRLF). */
    private static void lireEntetes(java.io.InputStream in) throws IOException {
        int etat = 0;
        int n = 0;
        while (etat < 4 && n < 8192) {
            int c = in.read();
            if (c < 0) throw new IOException("connexion fermée par le serveur");
            n++;
            if ((etat % 2 == 0 && c == '\r') || (etat % 2 == 1 && c == '\n')) etat++;
            else etat = (c == '\r') ? 1 : 0;
        }
    }
'''.split("\n")
L[c:c] = helper

s = "\n".join(L)
s = s.replace("Executors.newFixedThreadPool(4)", "Executors.newFixedThreadPool(2)")
open(f, "w", encoding="utf-8").write(s)
print("OK")
