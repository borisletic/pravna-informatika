package rs.ftn.pi.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Pokreće prateće lokalne servise pri startu aplikacije, tako da je dovoljan
 * jedan „Run" u IntelliJ-u: podiže <b>Ollama</b> (LLM) i <b>Python NLP servis</b>
 * ako već nisu pokrenuti. Procesi se gase pri gašenju aplikacije.
 *
 * Ponašanje se kontroliše preko {@code app.autostart.*} (vidi {@link AppConfig.Autostart}).
 * Sve je „best-effort": ako neki servis ne može da se pokrene (nije instaliran),
 * aplikacija svejedno radi (NLP/LLM imaju graceful fallback).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalServicesLauncher {

    private final AppConfig appConfig;
    private final List<Process> startedProcesses = new ArrayList<>();

    @EventListener(ApplicationReadyEvent.class)
    public void launch() {
        AppConfig.Autostart cfg = appConfig.getAutostart();
        if (cfg == null || !cfg.isEnabled()) {
            log.info("Autostart pratećih servisa je isključen (app.autostart.enabled=false).");
            return;
        }
        // u zasebnoj niti da ne blokira app (čekanje na portove)
        Thread t = new Thread(() -> {
            try {
                if (cfg.isOllama()) ensureOllama(cfg);
                if (cfg.isNlp()) ensureNlp(cfg);
            } catch (Exception e) {
                log.warn("Autostart pratećih servisa: {}", e.getMessage());
            }
        }, "local-services-launcher");
        t.setDaemon(true);
        t.start();
    }

    // ====================== Ollama ======================

    private void ensureOllama(AppConfig.Autostart cfg) {
        if (isPortOpen("localhost", 11434)) {
            log.info("Ollama već radi (port 11434).");
        } else {
            log.info("Pokrećem Ollama servis ('{}' serve)...", cfg.getOllamaBin());
            Process p = spawn(new File("."), logFile("ollama"), cfg.getOllamaBin(), "serve");
            if (p == null) {
                log.warn("Ollama nije pokrenut (nije instaliran ili nije na PATH-u). " +
                         "Instaliraj sa https://ollama.com — aplikacija nastavlja sa fallback-om.");
                return;
            }
            startedProcesses.add(p);
            waitForPort("localhost", 11434, 20);
        }
        // obezbedi model u pozadini (no-op ako je već povučen; download ako nije)
        log.info("Obezbeđujem Ollama model '{}' u pozadini (ollama pull)...", cfg.getOllamaModel());
        Process pull = spawn(new File("."), logFile("ollama-pull"), cfg.getOllamaBin(), "pull", cfg.getOllamaModel());
        if (pull != null) startedProcesses.add(pull);
    }

    // ====================== NLP servis (uvicorn) ======================

    private void ensureNlp(AppConfig.Autostart cfg) {
        if (isPortOpen("localhost", cfg.getNlpPort())) {
            log.info("NLP servis već radi (port {}).", cfg.getNlpPort());
            return;
        }
        File dir = resolveNlpDir(cfg.getNlpDir());
        if (dir == null) {
            log.warn("NLP direktorijum nije pronađen (probao: {}). Preskačem pokretanje NLP servisa.",
                    cfg.getNlpDir());
            return;
        }
        log.info("Pokrećem NLP servis (uvicorn) iz {}...", dir.getAbsolutePath());
        Process p = spawn(dir, logFile("nlp"),
                cfg.getPython(), "-m", "uvicorn", "main:app",
                "--host", "127.0.0.1", "--port", String.valueOf(cfg.getNlpPort()));
        if (p == null) {
            log.warn("NLP servis nije pokrenut. Proveri da je instaliran Python i zavisnosti: " +
                     "pip install -r nlp-service/requirements.txt");
            return;
        }
        startedProcesses.add(p);
        if (waitForPort("localhost", cfg.getNlpPort(), 25)) {
            log.info("NLP servis je podignut na portu {}.", cfg.getNlpPort());
        } else {
            log.warn("NLP servis se nije podigao na vreme — pogledaj {} za detalje.",
                    logFile("nlp").getAbsolutePath());
        }
    }

    // ====================== helperi ======================

    private Process spawn(File dir, File logOut, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logOut));
            return pb.start();
        } catch (Exception e) {
            log.debug("Ne mogu da pokrenem {}: {}", String.join(" ", command), e.getMessage());
            return null;
        }
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForPort(String host, int port, int seconds) {
        for (int i = 0; i < seconds; i++) {
            if (isPortOpen(host, port)) return true;
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    private File resolveNlpDir(String configured) {
        for (String c : new String[]{configured, "./nlp-service", "../nlp-service"}) {
            if (c == null) continue;
            File f = new File(c);
            if (new File(f, "main.py").exists()) return f.getAbsoluteFile();
        }
        return null;
    }

    private File logFile(String name) {
        File dir = new File(System.getProperty("java.io.tmpdir"), "pravna-informatika");
        dir.mkdirs();
        return new File(dir, name + ".log");
    }

    @PreDestroy
    public void stopAll() {
        for (Process p : startedProcesses) {
            try {
                if (p.isAlive()) {
                    p.destroy();
                    if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        p.destroyForcibly();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (!startedProcesses.isEmpty()) {
            log.info("Zaustavljeni prateći servisi pokrenuti od strane aplikacije.");
        }
    }
}
