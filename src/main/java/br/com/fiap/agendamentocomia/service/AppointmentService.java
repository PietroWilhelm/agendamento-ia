package br.com.fiap.agendamentocomia.service;

import br.com.fiap.agendamentocomia.model.Appointment;
import br.com.fiap.agendamentocomia.model.AppointmentStatus;
import br.com.fiap.agendamentocomia.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public List<Appointment> getAppointments() {
        return appointmentRepository.findAll();
    }

    public Appointment createAppointment(Appointment appointment) {
        return appointmentRepository.save(appointment);
    }

    public Appointment updateAppointment(Appointment appointment, Long appointmentId) {
        appointment.setId(appointmentId);
        return appointmentRepository.save(appointment);
    }

    public Optional<Appointment> getAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId);
    }

    public boolean isSlotTaken(String appointmentDate, String appointmentTime) {
        return appointmentRepository.existsByAppointmentDateAndAppointmentTimeAndStatus(
                appointmentDate, appointmentTime, AppointmentStatus.SCHEDULED);
    }

}
