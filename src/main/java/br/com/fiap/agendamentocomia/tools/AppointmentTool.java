package br.com.fiap.agendamentocomia.tools;

import br.com.fiap.agendamentocomia.model.Appointment;
import br.com.fiap.agendamentocomia.model.AppointmentStatus;
import br.com.fiap.agendamentocomia.service.AppointmentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;


@Component
public class AppointmentTool {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSING_TIME = LocalTime.of(19, 0);

    private static final long MIN_HOURS_TO_SCHEDULE = 24;
    private static final long MIN_HOURS_TO_CANCEL_OR_RESCHEDULE = 6;

    private final AppointmentService appointmentService;

    public AppointmentTool(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @Tool(description = "Retorna a data e a hora atuais (formato dd/MM/yyyy HH:mm). "
            + "Use sempre que precisar saber 'hoje', 'amanhã' ou validar antecedência mínima.")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DATE_TIME_FMT);
    }

    @Tool(description = "Consulta os dados de um agendamento pelo ID (cliente, serviço, data, horário e status). "
            + "Use antes de cancelar ou remarcar para conferir a antecedência.")
    public String findAppointment(@ToolParam(description = "ID do agendamento") Long appointmentId) {
        return appointmentService.getAppointment(appointmentId)
                .map(this::describe)
                .orElse("Agendamento " + appointmentId + " não encontrado.");
    }

    @Tool(description = "Realiza (cria) um novo agendamento de serviço no salão. "
            + "Só chame depois de ter nome completo, serviço (exatamente como na lista de serviços), data e horário. "
            + "Retorna a confirmação com o ID do agendamento ou o motivo da recusa.")
    public String scheduleAppointment(
            @ToolParam(description = "Nome completo do cliente") String clientName,
            @ToolParam(description = "Serviço, exatamente como consta na lista de serviços disponíveis") String service,
            @ToolParam(description = "Data no formato dd/MM/yyyy") String appointmentDate,
            @ToolParam(description = "Horário no formato HH:mm (24h)") String appointmentTime) {

        if (clientName == null || clientName.trim().split("\\s+").length < 2) {
            return "Recusado: é necessário informar o nome completo do cliente.";
        }
        if (service == null || service.isBlank()) {
            return "Recusado: é necessário informar o serviço.";
        }

        LocalDateTime start;
        try {
            start = parse(appointmentDate, appointmentTime);
        } catch (DateTimeParseException e) {
            return "Recusado: data/horário inválidos. Use dd/MM/yyyy e HH:mm.";
        }

        if (Duration.between(LocalDateTime.now(), start).toMinutes() < MIN_HOURS_TO_SCHEDULE * 60) {
            return "Recusado: o agendamento deve ser feito com pelo menos 24 horas de antecedência.";
        }
        String slotError = validateSlot(start, appointmentDate, appointmentTime);
        if (slotError != null) {
            return slotError;
        }

        Appointment saved = appointmentService.createAppointment(new Appointment(
                null, clientName.trim(), service.trim(),
                start.format(DATE_FMT), start.format(TIME_FMT), AppointmentStatus.SCHEDULED));

        return "Agendamento confirmado! " + describe(saved);
    }

    @Tool(description = "Cancela um agendamento existente pelo ID. O cancelamento deve ser pedido com pelo menos "
            + "6 horas de antecedência; com menos que isso pode haver taxa de 50% do valor do serviço "
            + "(avise o cliente e peça confirmação ANTES de chamar esta ferramenta nesse caso).")
    public String cancelAppointment(@ToolParam(description = "ID do agendamento") Long appointmentId) {
        var found = appointmentService.getAppointment(appointmentId);
        if (found.isEmpty()) {
            return "Agendamento " + appointmentId + " não encontrado.";
        }
        Appointment appointment = found.get();
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            return "Não é possível cancelar: o agendamento está com status " + appointment.getStatus() + ".";
        }

        LocalDateTime start = parse(appointment.getAppointmentDate(), appointment.getAppointmentTime());
        long hoursAhead = Duration.between(LocalDateTime.now(), start).toHours();
        if (start.isBefore(LocalDateTime.now())) {
            return "Não é possível cancelar: o horário do agendamento já passou.";
        }

        appointment.setStatus(AppointmentStatus.CANCELED);
        appointmentService.updateAppointment(appointment, appointment.getId());

        String result = "Agendamento cancelado. " + describe(appointment);
        if (hoursAhead < MIN_HOURS_TO_CANCEL_OR_RESCHEDULE) {
            result += " ATENÇÃO: cancelamento com menos de 6 horas de antecedência, "
                    + "pode ser cobrada taxa de 50% do valor do serviço.";
        }
        return result;
    }

    @Tool(description = "Remarca (re-agenda) um agendamento existente para nova data e horário. "
            + "Sem custo se pedido com pelo menos 6 horas de antecedência. "
            + "A nova data/horário precisa respeitar expediente e disponibilidade do salão.")
    public String rescheduleAppointment(
            @ToolParam(description = "ID do agendamento") Long appointmentId,
            @ToolParam(description = "Nova data no formato dd/MM/yyyy") String newDate,
            @ToolParam(description = "Novo horário no formato HH:mm (24h)") String newTime) {

        var found = appointmentService.getAppointment(appointmentId);
        if (found.isEmpty()) {
            return "Agendamento " + appointmentId + " não encontrado.";
        }
        Appointment appointment = found.get();
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            return "Não é possível remarcar: o agendamento está com status " + appointment.getStatus() + ".";
        }

        LocalDateTime currentStart = parse(appointment.getAppointmentDate(), appointment.getAppointmentTime());
        if (currentStart.isBefore(LocalDateTime.now())) {
            return "Não é possível remarcar: o horário do agendamento já passou.";
        }

        LocalDateTime newStart;
        try {
            newStart = parse(newDate, newTime);
        } catch (DateTimeParseException e) {
            return "Recusado: data/horário inválidos. Use dd/MM/yyyy e HH:mm.";
        }
        if (!newStart.isAfter(LocalDateTime.now())) {
            return "Recusado: a nova data e horário precisam estar no futuro.";
        }
        String slotError = validateSlot(newStart, newDate, newTime);
        if (slotError != null) {
            return slotError;
        }

        long hoursAhead = Duration.between(LocalDateTime.now(), currentStart).toHours();

        appointment.setAppointmentDate(newStart.format(DATE_FMT));
        appointment.setAppointmentTime(newStart.format(TIME_FMT));
        appointmentService.updateAppointment(appointment, appointment.getId());

        String result = "Agendamento remarcado! " + describe(appointment);
        if (hoursAhead < MIN_HOURS_TO_CANCEL_OR_RESCHEDULE) {
            result += " ATENÇÃO: remarcação com menos de 6 horas de antecedência, "
                    + "a remarcação sem custos não se aplica.";
        }
        return result;
    }

    /** Expediente (seg-sáb, 09h-19h, sem domingos) e disponibilidade do horário. Retorna null se estiver ok. */
    private String validateSlot(LocalDateTime start, String date, String time) {
        if (start.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return "Recusado: não há atendimento aos domingos.";
        }
        LocalTime t = start.toLocalTime();
        if (t.isBefore(OPENING_TIME) || !t.isBefore(CLOSING_TIME)) {
            return "Recusado: o expediente é de segunda a sábado, das 09h00 às 19h00.";
        }
        if (appointmentService.isSlotTaken(start.format(DATE_FMT), start.format(TIME_FMT))) {
            return "Recusado: já existe um agendamento em " + date + " às " + time + ". Sugira outro horário.";
        }
        return null;
    }

    private LocalDateTime parse(String date, String time) {
        return LocalDateTime.of(
                java.time.LocalDate.parse(date.trim(), DATE_FMT),
                LocalTime.parse(time.trim(), TIME_FMT));
    }

    private String describe(Appointment a) {
        return "ID: " + a.getId()
                + " | Cliente: " + a.getClientName()
                + " | Serviço: " + a.getService()
                + " | Data: " + a.getAppointmentDate()
                + " | Horário: " + a.getAppointmentTime()
                + " | Status: " + a.getStatus();
    }
}

