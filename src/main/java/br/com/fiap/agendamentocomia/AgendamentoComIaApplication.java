package br.com.fiap.agendamentocomia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.component.page.AppShellConfigurator;

@SpringBootApplication
public class AgendamentoComIaApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(AgendamentoComIaApplication.class, args);
    }
}
