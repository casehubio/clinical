package io.casehub.clinical.report;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.report.model.IndSafetyReport;
import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.qute.Template;
import io.quarkus.qute.Location;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@HandWrittenEndpoint("regulatory reports — PDF/JSON content negotiation, trial-scoped aggregation")
@Path("/api/reports")
@RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.MONITOR})
public class ReportResource {

    @Inject IndSafetyReportService indSafetyService;
    @Inject PdfGenerator pdfGenerator;
    @Inject CurrentPrincipal principal;

    @Location("reports/ind-safety")
    Template indSafetyTemplate;

    @GET
    @Path("/ind-safety")
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response indSafety(@QueryParam("trialId") UUID trialId,
                               @QueryParam("from") String from,
                               @QueryParam("to") String to,
                               @Context HttpHeaders headers) {
        Instant fromInstant = LocalDate.parse(from).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = LocalDate.parse(to).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        var report = indSafetyService.generate(trialId, fromInstant, toInstant, principal.tenancyId());

        if (wantsPdf(headers)) {
            String html = indSafetyTemplate.data("report", report).render();
            var options = new PdfOptions("IND Safety Report — " + from + " to " + to,
                    "CaseHub Clinical", Instant.now(), "ind-safety", null);
            byte[] pdf = pdfGenerator.generateFromHtml(html, options).orElseThrow();
            return Response.ok(pdf, "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"ind-safety-" + from + "-" + to + ".pdf\"")
                    .build();
        }

        return Response.ok(report, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @GET
    @Path("/audit-trail")
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response auditTrail(@QueryParam("trialId") UUID trialId, @Context HttpHeaders headers) {
        return Response.ok(Map.of(
                "status", "stub",
                "message", "Audit trail export — pending casehub-ledger-reporting SNAPSHOT",
                "trialId", trialId.toString()
        )).build();
    }

    @GET
    @Path("/compliance")
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response compliance(@QueryParam("trialId") UUID trialId, @Context HttpHeaders headers) {
        return Response.ok(Map.of(
                "status", "stub",
                "message", "EU AI Act Art.12 compliance report — pending casehub-ledger-reporting SNAPSHOT",
                "trialId", trialId.toString()
        )).build();
    }

    @GET
    @Path("/merkle-verification")
    @Produces(MediaType.APPLICATION_JSON)
    public Response merkleVerification(@QueryParam("trialId") UUID trialId) {
        return Response.ok(Map.of(
                "status", "stub",
                "message", "Merkle verification bundle — pending casehub-ledger-reporting SNAPSHOT",
                "trialId", trialId.toString()
        )).build();
    }

    private static boolean wantsPdf(HttpHeaders headers) {
        var accept = headers.getAcceptableMediaTypes();
        return accept.stream().anyMatch(mt ->
                mt.getType().equals("application") && mt.getSubtype().equals("pdf"));
    }
}
