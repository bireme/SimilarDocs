
package org.bireme.sds;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bireme.sd.service.TopIndex;

import scala.collection.mutable.HashSet;
import scala.collection.mutable.Set;

/**
 *
 * @author Heitor Barbieri
 * date 20161024
 */
public class SDService extends HttpServlet {
    private TopIndex topIndex;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();

        final String sdIndexPath = context.getInitParameter("SD_INDEX_PATH");
        if (sdIndexPath == null) throw new ServletException(
                                              "empty 'SD_INDEX_PATH' config");
        final String otherIndexPath = context.getInitParameter("OTHER_INDEX_PATH");
        if (otherIndexPath == null) throw new ServletException(
                                              "empty 'OTHER_INDEX_PATH' config");
        final String docIndexPath = otherIndexPath +
                         (otherIndexPath.endsWith("/") ? "" : "/") + "docIndex";
        final String topIndexPath = otherIndexPath +
                         (otherIndexPath.endsWith("/") ? "" : "/") + "topIndex";
        Set<String> fields = new HashSet<>();
        fields.add("ti");
        fields.add("ti_de");
        fields.add("ti_en");
        fields.add("ti_es");
        fields.add("ti_fr");
        fields.add("ti_it");
        fields.add("ti_pt");
        fields.add("ab");
        fields.add("ab_de");
        fields.add("ab_en");
        fields.add("ab_es");
        fields.add("ab_fr");
        fields.add("ab_it");
        fields.add("ab_pt");

        Tools.deleteLockFile(docIndexPath);
        Tools.deleteLockFile(topIndexPath);

        topIndex = new TopIndex(sdIndexPath, docIndexPath, topIndexPath,
                                                                fields.toSet());
        context.setAttribute("MAINTENANCE_MODE", Boolean.FALSE);
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(final HttpServletRequest request,
                                  final HttpServletResponse response)
                                                        throws ServletException,
                                                                   IOException {
        response.setContentType("text/xml;charset=UTF-8");
        
        final ServletContext context = request.getServletContext();
        final boolean maintenanceMode = 
                              (Boolean)context.getAttribute("MAINTENANCE_MODE");
        try (PrintWriter out = response.getWriter()) {           
            final String maintenance = request.getParameter("maintenance");
            if (maintenance == null) {
                if (maintenanceMode) {
                    out.println("<WARNING>System in maintenance mode</WARNING>");
                    return;
                }
                final String psId = request.getParameter("psId");
                if (psId == null) {
                    out.println("<ERROR>missing 'psId' parameter</ERROR>");
                } else {
                    final String addProfile = request.getParameter("addProfile");
                    if (addProfile == null) {
                        final String deleteProfile = request.getParameter(
                                                               "deleteProfile");
                        if (deleteProfile == null) {
                            final String getSimDocs = request.getParameter(
                                                                  "getSimDocs");
                            if (getSimDocs == null) {
                                final String showProfiles = request.getParameter(
                                                                "showProfiles");
                                if (showProfiles == null) usage(out);
                                else out.println(topIndex.getProfilesXml(psId));
                            } else {
                                final String[] profs = getSimDocs.split(" *\\, *");
                                Set<String> profiles = new HashSet<>();
                                for (String prof: profs) {
                                    profiles.add(prof);
                                }
                                final String outFields = request.getParameter(
                                                                   "outFields");
                                final String[] oFields = (outFields == null)
                                                   ?  new String[0]
                                                   : outFields.split(" *\\, *");
                                Set<String> fields = new HashSet<>();
                                for (String fld: oFields) {
                                    fields.add(fld);
                                }
                                out.println(topIndex.getSimDocsXml(psId,
                                         profiles.toSet(), fields.toSet(), 10));
                            }
                        } else {
                            topIndex.deleteProfile(psId, deleteProfile);
                            out.println("<result>OK</result>");
                        }
                    } else {
                        final String sentence = request.getParameter("sentence");
                        if (sentence == null) usage(out);
                        else {
                            topIndex.addProfile(psId, addProfile, sentence);
                            out.println("<result>OK</result>");
                        }
                    }
                }
            } else {
                context.setAttribute("MAINTENANCE_MODE", 
                                     Boolean.valueOf(maintenance));
                out.println("<result>MAINTENANCE_MODE=" + maintenance + 
                                                                   "</result>");
            }
        }
    }

    private void usage(final PrintWriter out) {
        out.println("<SYNTAX>");
        out.println("SDService/?psId=&lt;id&gt;");
        out.println("--- and one of the following options: ---");
        out.println("&amp;addProfile=&lt;id&gt;&amp;sentence=&lt;sentence&gt;");
        out.println("&amp;deleteProfile=&lt;id&gt;");
        out.println("&amp;getSimDocs=&lt;profile&gt;,..,&lt;profile&gt;&amp;outFields=&lt;field&gt;,...,&lt;field&gt;");
        out.println("&amp;showProfiles=true");
        out.println("</SYNTAX>");
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
