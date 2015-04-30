import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.Session;

import com.edlio.emailreplyparser.EmailReplyParser;
import com.sun.mail.smtp.SMTPMessage;
import no.finntech.mail.Content;
import no.finntech.mail.messaging.ContentFilter;
import no.finntech.mail.messaging.MessageConverter;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.*;

public class ContentFilterImplTest {
    private static final String TEXT = "This is a text";
    private static final String HTML_LEAD_IN = "<html><head></head><body>";
    private static final String HTML_TAIL_OUT = "</body></html>";
    private static final String HTMLTEXT = HTML_LEAD_IN + TEXT + HTML_TAIL_OUT;
    private static final String REPLY = "Dette er mitt svar.";
    private static final String DATE_PATTERN = "Message @ 2014-04-24 15:16:47 +0200";
    private static ContentFilter contentFilter;
    @BeforeClass
    public static void init() {
        contentFilter = new WrappingContentFilter();
    }

    @Test
    public void testFilterContentUncontaminatedPlainText() throws Exception {
        String filteredContents = contentFilter.filterContent("", TEXT);
        assertThat(filteredContents, is(TEXT));
    }

    @Test
    public void testFilterContentUncontaminatedHtml() throws Exception {
        String filteredContents = contentFilter.filterContent(TEXT, "");
        assertThat(filteredContents, is(TEXT));
    }

    @Test
    public void testFilterContentUncontaminatedBoth() throws Exception {
        String filteredContents = contentFilter.filterContent(TEXT, TEXT);
        assertThat(filteredContents, is(TEXT));
    }

    @Test
    public void testFilterContentContaminatedBoth() throws Exception {
        String filteredContents = contentFilter.filterContent(HTMLTEXT, TEXT);
        assertThat(filteredContents, is(TEXT));
    }

    @Test
    public void testFilterContentContaminatedHtml() throws Exception {
        String filteredContents = contentFilter.filterContent(HTMLTEXT, "");
        assertThat(filteredContents, is(TEXT));
    }

    @Test
    public void testFilterContentWithRepliedPlainText() throws Exception {
        String filteredContents = contentFilter.filterContent("", "> Reply\n" + TEXT);
        assertThat(filteredContents, is(TEXT));
    }

    @Test
    public void testUnintentionalDateStrip() {
        String filteredContents = contentFilter.filterContent(DATE_PATTERN, DATE_PATTERN);
        assertThat(filteredContents, is(DATE_PATTERN));
    }

    @Test
    public void testFilterContentWithAnotherFlavorOfRepliedPlainText() throws Exception {
        String filteredContents = contentFilter.filterContent("", "> Reply\n" + TEXT);
        assertThat(filteredContents, is(TEXT));
    }

    @Test
    public void testFilterContentWithReallyRepliedPlainText() {
        String stuff = "Den 21. feb. 2012 15:12, skrev FINN-bruker:\n" +
                "> \tFINN.no \t\n" +
                "> \t\n" +
                ">\n" +
                ">\n" +
                ">   Test mail for SMTPServer. Mail sent to SMTP server and resent to\n" +
                ">   mail module.\n" +
                ">\n" +
                ">\n" +
                ">\n" +
                "> Hilsen FINN.no\n" +
                ">\n" +
                "> \t\n" +
                ">\n" +
                REPLY;
        String filteredContents = contentFilter.filterContent("", stuff);
        assertNotNull(filteredContents);
        assertThat(filteredContents, is(REPLY));
    }

    @Test
    public void testGmailWithTimeAndTimezone() {
        final String plain = "Eposten har ikke linjeskift.\n" +
                "\n" +
                "Anonym\n" +
                "\n" +
                "2014-07-29 12:32 GMT+02:00 Anonym Bruker via FINN.no <samtale-xxxxx123@innboks.finn.no>:\n" +
                "Denne Skal Ha Linjeskift\n" +
                "\n" +
                "\n" +
                "-- \n" +
                "Hilsen\n" +
                "En annen anonym bruker";   //

        final String filteredContent = contentFilter.filterContent("", plain);
        assertThat(filteredContent, is("Eposten har ikke linjeskift.\n\nAnonym"));
    }

    @Test
    public void testFilterContentWithReallyRepliedHtml() {
        String stuff = HTML_LEAD_IN +
                "Den 21. feb. 2012 15:12, skrev FINN-bruker:\n" +
                "> \tFINN.no \t\n" +
                "> \t\n" +
                ">\n" +
                ">\n" +
                ">   Test mail for SMTPServer. Mail sent to SMTP server and resent to\n" +
                ">   mail module.\n" +
                ">\n" +
                ">\n" +
                ">\n" +
                "> Hilsen FINN.no\n" +
                ">\n" +
                "> \t\n" +
                ">\n" +
                REPLY +
                HTML_TAIL_OUT;
        String filteredContents = contentFilter.filterContent(stuff, "");
        assertNotNull(filteredContents);
        assertThat(filteredContents, is(REPLY));
    }

    @Test
    public void testFilterContentWithReallyRepliedHtmlAsSentFromGmail() {
        String plain = "Svar fra gmail\n" +
                '\n' +
                '\n' +
                "On Thu, Dec 13, 2012 at 7:06 AM, FINN-bruker <finnbruker@innboks.finn.no>wrote:\n" +
                '\n' +
                "> Svar fra finnboks\n" +
                '>';
        String html = "Svar fra gmail<div><br><br><div>On Thu, Dec 13, 2012 at 7:06 AM, FINN-bruker <span>&lt;<a href=\"mailto:finnbruker@innboks.finn.no\" target=\"_blank\">finnbruker@innboks.finn.no</a>&gt;</span> wrote:<br>\n" +
                "<blockquote><div>Svar fra finnboks</div>\n" +
                "</blockquote></div><br></div>";
        String filteredContents = contentFilter.filterContent(html, plain);
        assertNotNull(filteredContents);
        assertThat(filteredContents, is("Svar fra gmail"));
    }

    @Test
    public void testFilterContentWithReallyRepliedHtmlAsSentFromGmailJoselito() {
        // Real mail from Joselitos Gmail account
        final String plain = "fra gmail til finn\n" +
                '\n' +
                '\n' +
                "2013/1/29 test testesen " +
                "<samtale-hokuspokus@innboks.finn.no>\n" +
                "> Hei!\n" +
                "> FINN.no har sendt deg en melding på vegne av Test Testesen\n" +
                "> til gmail fra finn\n" +
                "> Hilsen \n" +
                "> Test Testesen \n" +
                "> \n" +
                "> Henvendelsen gjelder annonse med FINN-kode 0\n" +
                "> <http://www.finn.no/finn/object?finnkode=0>\n" +
                "> \n" +
                "> Stusser du over avsenders e-postadresse?\n" +
                "> \n" +
                "> For å redusere muligheten for spam ser verken du eller mottaker hverandres\n" +
                "> e-postadresse. \n" +
                "> Det fungerer fortsatt å trykke svar/reply i e-posten. Mer om falske e-poster\n" +
                "> <http://ks.finn.no/trygg-pa-finn/falske-eposter>\n" +
                "> \n" +
                "> ";
        final String html = "<html>\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n" +
                "</head>\n" +
                "<body>\n" +
                "<div>fra gmail til finn</div>\n" +
                "<div><br>\n" +
                "<br>\n" +
                "<div>2013/1/29 Test Testesen <span><<a href=\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>></span><br>\n" +
                "<blockquote>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<h3 style=\"\">Hei!</h3>\n" +
                "<p>FINN.no har sendt deg en melding på vegne av Test Testesen </p>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<div>til gmail fra finn</div>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>Hilsen <br>\n" +
                "Test Testesen</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<p>Henvendelsen gjelder <a href=\"http://www.finn.no/finn/object?finnkode=0\" target=\"_blank\">\n" +
                "annonse med FINN-kode 0</a> </p>\n" +
                "<p></p>\n" +
                "<strong>Stusser du over avsenders e-postadresse?</strong>\n" +
                "<p>For å redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse.\n" +
                "<br>\n" +
                "Det fungerer fortsatt å trykke svar/reply i e-posten. <a href=\"http://ks.finn.no/trygg-pa-finn/falske-eposter\" target=\"_blank\">\n" +
                "Mer om falske e-poster</a></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "</blockquote>\n" +
                "</div>\n" +
                "<br>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>";
        String filteredContents = contentFilter.filterContent(html, plain);
        assertThat(filteredContents, is("fra gmail til finn"));
    }

    @Test
    public void testFilterContentWithReallyRepliedHtmlAsSentFromGmailBottomPosted() {
        String plain =
                "\n\n" +
                        "On Thu, Dec 13, 2012 at 7:06 AM, FINN-bruker <finnbruker@innboks.finn.no>wrote:\n" +
                        '\n' +
                        "> Svar fra finnboks\n" +
                        ">\n" +
                        "Svar fra gmail\n";
        String html = "<div><br><br><div>On Thu, Dec 13, 2012 at 7:06 AM, FINN-bruker <span>&lt;<a href=\"mailto:finnbruker@innboks.finn.no\" target=\"_blank\">finnbruker@innboks.finn.no</a>&gt;</span> wrote:<br>\n" +
                "<blockquote><div>Svar fra finnboks</div>\n" +
                "</blockquote></div><br></div>Svar fra gmail";
        String filteredContents = contentFilter.filterContent(html, plain);
        assertNotNull(filteredContents);
        assertThat(filteredContents, is("Svar fra gmail"));
    }

    @Test
    public void testFilterContentWithRepliedHtmlFromGMailToOutlookTopPosted() {
        String realContentsFromOutlookMail =
                "Dette var da bra da<br>Hilsen Test Testesen<br><br><div>Den 05:24 6. mars 2012 skrev  <span>&lt;" +
                        "<a href=\"mailto:agent@finn.no\">agent@finn.no</a>&gt;</span> følgende:<br>" +
                        "<blockquote class=\"gmail_quote\" " +
                        "style=\"margin:0 0 0 .8ex;border-left:1px #ccc solid;padding-left:1ex\">\r\n" +
                        "<div> " +
                        "<table>" +
                        "    <tbody>    <tr>        " +
                        "<td>  </td>" +
                        "    </tr> <tr align=\"left\">\r\n" +
                        "<td>" +
                        "   </td>        <td> " +
                        "<img src=\"http://cache.finn.no/img/mail/logo.gif\" alt=\"FINN.no\" style=\"display:block\" " +
                        "height=\"39\" width=\"120\"> </td>\r\n" +
                        "<td>   </td>    </tr>    <tr>" +
                        "        <td>" +
                        "  </td>    </tr>    <tr align=\"left\">        <td style=\"width:10px\" bgcolor=\"#FFFFFF\" " +
                        "width=\"10px\">\r\n" +
                        "  </td>        <td>            <table>" +
                        " <tbody bgcolor=\"#FFFFFF\">            <tr>" +
                        "                <td>  </td>            </tr>" +
                        "            <tr>" +
                        "                <td>\r\n" +
                        "<table>\r\n" +
                        "<tbody><tr>\r\n" +
                        "<td></td>\r\n" +
                        "<td>\r\n" +
                        "<h3>Hei Test Testesen!</h3>\r\n" +
                        "<p>FINN har fått inn følgende annonser som passer til ditt søk:</p>\r\n" +
                        "<h4>\r\n" +
                        "        Testagent</h4>\r\n" +
                        "<table>\r\n" +
                        "<tbody><tr bgcolor=\"#CCCCCC\">\r\n" +
                        "<th>Bilde</th>\r\n" +
                        "<th>Overskrift</th>\r\n" +
                        "<th>Sted</th>\r\n" +
                        "<th>Pris</th>\r\n" +
                        "</tr>\r\n" +
                        "\r\n" +
                        "<tr>\r\n" +
                        "<td>\r\n" +
                        "<a href=\"http://www.finn.no/finn/object?finnkode=0&amp;WT.synd_type=agent\"" +
                        " target=\"_blank\">\r\n" +
                        "<img src=\"http://finncdn.no//mmo/2012/3/vertical-3/05/4/335/757/0.jpg\"" +
                        " border=\"0\">\r\n" +
                        "</a>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "<a href=\"http://www.finn.no/finn/object?finnkode=0&amp;WT.synd_type=agent\"" +
                        " target=\"_blank\">\r\n" +
                        "        Skoda\r\n" +
                        "\r\n" +
                        "Yeti\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "2.0 140 hk 4 x 4 TDI Experience\r\n" +
                        "2010-mod\r\n" +
                        "52000 km\r\n" +
                        "        </a>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "        der\r\n" +
                        "        </td>\r\n" +
                        "<td>\r\n" +
                        "        329 584,-\r\n" +
                        "        </td>\r\n" +
                        "</tr>\r\n" +
                        "\r\n" +
                        "</tbody></table>\r\n" +
                        "\r\n" +
                        "<br>\r\n" +
                        "\r\n" +
                        "<p>Dette er en automatisk generert e-post, dersom du ønsker å rette en henvendelse til" +
                        " Kundesenteret, vennligst benytt\r\n" +
                        "følgende e-post adresse: " +
                        "<a href=\"mailto:kundesenter@finn.no\" target=\"_blank\">kundesenter@finn.no</a></p>\r\n" +
                        "\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "\r\n" +
                        "<h4>Endre søk?</h4>\r\n" +
                        "\r\n" +
                        "<p> Dersom du ønsker å endre dette søket, kan du gå til\r\n" +
                        "        <a href=\"http://www.finn.no/finn/minfinn/searches/list\" target=\"_blank\">" +
                        "Mine autosøk</a>\r\n" +
                        "</p>\r\n" +
                        "<h4>Stoppe søket?</h4>\r\n" +
                        "\r\n" +
                        "<p> Hvis du ønsker å stoppe søket ditt kan du gjøre dette ved å\r\n" +
                        "<a href=\"http://www.finn.no/finn/minfinn/searches/stop?channelId=3&amp;alertId=0&amp;" +
                        "userId=0\" target=\"_blank\">klikke her</a>\r\n" +
                        "</p>\r\n" +
                        "\r\n" +
                        "<h4>Kjør søket på FINN?</h4>\r\n" +
                        "\r\n" +
                        "<p> Trykk\r\n" +
                        "        <a href=\"http://www.finn.no/finn/minfinn/searches/search?searchId=2&amp;" +
                        "alertId=4623472\" target=\"_blank\">her</a>\r\n" +
                        "for å finne de nyeste annonsene på FINN som matcher dette søket\r\n" +
                        "        </p>\r\n" +
                        "</td><td>\r\n" +
                        "</td>\r\n" +
                        "</tr>\r\n" +
                        "</tbody></table>\r\n" +
                        "\r\n" +
                        "<div><br><br>Hilsen FINN.no<br>" +
                        "</div></td></tr><tr><td> </td></tr></tbody>" +
                        "</table></td><td> </td>\r\n" +
                        "</tr><tr><td>" +
                        " </td></tr></tbody></table></div>\r\n" +
                        "</blockquote></div>\r\n";
        String filteredContents = contentFilter.filterContent(realContentsFromOutlookMail, "");
        assertNotNull(filteredContents);
        assertThat(filteredContents, is("Dette var da bra da\nHilsen Test Testesen"));
    }
    @Test
    public void testFilterContentWithRepliedHtmlFromGMailToOutlookBottomPosted() {
        String realContentsFromOutlookMail =
                "<br><br><div>Den 05:24 6. mars 2012 skrev  <span>&lt;" +
                        "<a href=\"mailto:agent@finn.no\">agent@finn.no</a>&gt;</span> følgende:<br>" +
                        "<blockquote class=\"gmail_quote\" " +
                        "style=\"margin:0 0 0 .8ex;border-left:1px #ccc solid;padding-left:1ex\">\r\n" +
                        "<div> " +
                        "<table>" +
                        "    <tbody>    <tr>        " +
                        "<td>  </td>" +
                        "    </tr> <tr align=\"left\">\r\n" +
                        "<td>" +
                        "   </td>        <td> " +
                        "<img src=\"http://cache.finn.no/img/mail/logo.gif\" alt=\"FINN.no\" style=\"display:block\" " +
                        "height=\"39\" width=\"120\"> </td>\r\n" +
                        "<td>   </td>    </tr>    <tr>" +
                        "        <td>" +
                        "  </td>    </tr>    <tr align=\"left\">        <td style=\"width:10px\" bgcolor=\"#FFFFFF\" " +
                        "width=\"10px\">\r\n" +
                        "  </td>        <td>            <table>" +
                        " <tbody bgcolor=\"#FFFFFF\">            <tr>" +
                        "                <td>  </td>            </tr>" +
                        "            <tr>" +
                        "                <td>\r\n" +
                        "<table>\r\n" +
                        "<tbody><tr>\r\n" +
                        "<td></td>\r\n" +
                        "<td>\r\n" +
                        "<h3>Hei Test Testesen!</h3>\r\n" +
                        "<p>FINN har fått inn følgende annonser som passer til ditt søk:</p>\r\n" +
                        "<h4>\r\n" +
                        "        Testagent</h4>\r\n" +
                        "<table>\r\n" +
                        "<tbody><tr bgcolor=\"#CCCCCC\">\r\n" +
                        "<th>Bilde</th>\r\n" +
                        "<th>Overskrift</th>\r\n" +
                        "<th>Sted</th>\r\n" +
                        "<th>Pris</th>\r\n" +
                        "</tr>\r\n" +
                        "\r\n" +
                        "<tr>\r\n" +
                        "<td>\r\n" +
                        "<a href=\"http://www.finn.no/finn/object?finnkode=0&amp;WT.synd_type=agent\"" +
                        " target=\"_blank\">\r\n" +
                        "<img src=\"http://finncdn.no//mmo/2012/3/vertical-3/05/4/335/757/84_1502303795_thumb.jpg\"" +
                        " border=\"0\">\r\n" +
                        "</a>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "<a href=\"http://www.finn.no/finn/object?finnkode=0&amp;WT.synd_type=agent\"" +
                        " target=\"_blank\">\r\n" +
                        "        Skoda\r\n" +
                        "\r\n" +
                        "Yeti\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "2.0 140 hk 4 x 4 TDI Experience\r\n" +
                        "2010-mod\r\n" +
                        "52000 km\r\n" +
                        "        </a>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "        Rasta\r\n" +
                        "        </td>\r\n" +
                        "<td>\r\n" +
                        "        329 584,-\r\n" +
                        "        </td>\r\n" +
                        "</tr>\r\n" +
                        "\r\n" +
                        "</tbody></table>\r\n" +
                        "\r\n" +
                        "<br>\r\n" +
                        "\r\n" +
                        "<p>Dette er en automatisk generert e-post, dersom du ønsker å rette en henvendelse til" +
                        " Kundesenteret, vennligst benytt\r\n" +
                        "følgende e-post adresse: " +
                        "<a href=\"mailto:kundesenter@finn.no\" target=\"_blank\">kundesenter@finn.no</a></p>\r\n" +
                        "\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "\r\n" +
                        "<h4>Endre søk?</h4>\r\n" +
                        "\r\n" +
                        "<p> Dersom du ønsker å endre dette søket, kan du gå til\r\n" +
                        "        <a href=\"http://www.finn.no/finn/minfinn/searches/list\" target=\"_blank\">" +
                        "Mine autosøk</a>\r\n" +
                        "</p>\r\n" +
                        "<h4>Stoppe søket?</h4>\r\n" +
                        "\r\n" +
                        "<p> Hvis du ønsker å stoppe søket ditt kan du gjøre dette ved å\r\n" +
                        "<a href=\"http://www.finn.no/finn/minfinn/searches/stop?channelId=0&amp;alertId=0&amp;" +
                        "userId=0\" target=\"_blank\">klikke her</a>\r\n" +
                        "</p>\r\n" +
                        "\r\n" +
                        "<h4>Kjør søket på FINN?</h4>\r\n" +
                        "\r\n" +
                        "<p> Trykk\r\n" +
                        "        <a href=\"http://www.finn.no/finn/minfinn/searches/search?searchId=0&amp;" +
                        "alertId=0\" target=\"_blank\">her</a>\r\n" +
                        "for å finne de nyeste annonsene på FINN som matcher dette søket\r\n" +
                        "        </p>\r\n" +
                        "</td><td>\r\n" +
                        "</td>\r\n" +
                        "</tr>\r\n" +
                        "</tbody></table>\r\n" +
                        "\r\n" +
                        "<div><br><br>Hilsen FINN.no<br>" +
                        "</div></td></tr><tr><td></td></tr></tbody>" +
                        "</table></td><td></td>\r\n" +
                        "</tr><tr><td>" +
                        "</td></tr></tbody></table></div>\r\n" +
                        "</blockquote></div>Dette var da bra da<br>Hilsen Test<br>\r\n";
        String filteredContents = contentFilter.filterContent(realContentsFromOutlookMail, "");
        assertNotNull(filteredContents);
        assertThat(filteredContents, is("Dette var da bra da\nHilsen Test")); // No bottom-post markers detected...
    }
    @Test
    public void testFilterContentWithRepliedHtmlFromGMailToOutlookBottomPosted2() {
        String realContentsFromOutlookMail =
                "<br><br><div>05:24 6. mars 2012 skrev  <span>&lt;" +
                        "<a href=\"mailto:agent@finn.no\">agent@finn.no</a>&gt;</span> følgende:<br>" +
                        "<blockquote>\r\n" +
                        "<div> " +
                        "<table>" +
                        "    <tbody>    <tr>        " +
                        "<td>  </td>" +
                        "    </tr> <tr align=\"left\">\r\n" +
                        "<td>" +
                        "   </td>        <td> " +
                        "<img src=\"http://cache.finn.no/img/mail/logo.gif\" alt=\"FINN.no\"> </td>\r\n" +
                        "<td>   </td>    </tr>    <tr>" +
                        "        <td>" +
                        "  </td>    </tr>    <tr align=\"left\">        <td>\r\n" +
                        "  </td>        <td>            <table>" +
                        " <tbody bgcolor=\"#FFFFFF\">            <tr>" +
                        "                <td>  </td>            </tr>" +
                        "            <tr>" +
                        "                <td>\r\n" +
                        "<table>\r\n" +
                        "<tbody><tr>\r\n" +
                        "<td></td>\r\n" +
                        "<td>\r\n" +
                        "<h3>Hei Test Testesen!</h3>\r\n" +
                        "<p>FINN har fått inn følgende annonser som passer til ditt søk:</p>\r\n" +
                        "<h4>\r\n" +
                        "        Testagent</h4>\r\n" +
                        "<table>\r\n" +
                        "<tbody><tr bgcolor=\"#CCCCCC\">\r\n" +
                        "<th>Bilde</th>\r\n" +
                        "<th>Overskrift</th>\r\n" +
                        "<th>Sted</th>\r\n" +
                        "<th>Pris</th>\r\n" +
                        "</tr>\r\n" +
                        "\r\n" +
                        "<tr>\r\n" +
                        "<td>\r\n" +
                        "<a href=\"http://www.finn.no/finn/object?finnkode=0&amp;WT.synd_type=agent\"" +
                        " target=\"_blank\">\r\n" +
                        "<img src=\"http://finncdn.no//mmo/2012/3/vertical-3/05/4/335/757/0.jpg\"" +
                        " border=\"0\">\r\n" +
                        "</a>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "<a href=\"http://www.finn.no/finn/object?finnkode=0&amp;WT.synd_type=agent\"" +
                        " target=\"_blank\">\r\n" +
                        "        Skoda\r\n" +
                        "\r\n" +
                        "Yeti\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "2.0 140 hk 4 x 4 TDI Experience\r\n" +
                        "2010-mod\r\n" +
                        "52000 km\r\n" +
                        "        </a>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "        der\r\n" +
                        "        </td>\r\n" +
                        "<td>\r\n" +
                        "        329 584,-\r\n" +
                        "        </td>\r\n" +
                        "</tr>\r\n" +
                        "\r\n" +
                        "</tbody></table>\r\n" +
                        "\r\n" +
                        "<br>\r\n" +
                        "\r\n" +
                        "<p>Dette er en automatisk generert e-post, dersom du ønsker å rette en henvendelse til" +
                        " Kundesenteret, vennligst benytt\r\n" +
                        "følgende e-post adresse: " +
                        "<a href=\"mailto:kundesenter@finn.no\" target=\"_blank\">kundesenter@finn.no</a></p>\r\n" +
                        "\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "</td>\r\n" +
                        "<td>\r\n" +
                        "\r\n" +
                        "<h4>Endre søk?</h4>\r\n" +
                        "\r\n" +
                        "<p> Dersom du ønsker å endre dette søket, kan du gå til\r\n" +
                        "        <a href=\"http://www.finn.no/finn/minfinn/searches/list\" target=\"_blank\">" +
                        "Mine autosøk</a>\r\n" +
                        "</p>\r\n" +
                        "<h4>Stoppe søket?</h4>\r\n" +
                        "\r\n" +
                        "<p> Hvis du ønsker å stoppe søket ditt kan du gjøre dette ved å\r\n" +
                        "<a href=\"http://www.finn.no/finn/minfinn/searches/stop?channelId=3&amp;alertId=0&amp;" +
                        "userId=0\" target=\"_blank\">klikke her</a>\r\n" +
                        "</p>\r\n" +
                        "\r\n" +
                        "<h4>Kjør søket på FINN?</h4>\r\n" +
                        "\r\n" +
                        "<p> Trykk\r\n" +
                        "        <a href=\"http://www.finn.no/finn/minfinn/searches/search?searchId=0&amp;" +
                        "alertId=0\" target=\"_blank\">her</a>\r\n" +
                        "for å finne de nyeste annonsene på FINN som matcher dette søket\r\n" +
                        "        </p>\r\n" +
                        "</td><td>\r\n" +
                        "</td>\r\n" +
                        "</tr>\r\n" +
                        "</tbody></table>\r\n" +
                        "\r\n" +
                        "<div><br><br>Hilsen FINN.no<br>" +
                        "</div></td></tr><tr><td></td></tr></tbody>" +
                        "</table></td><td></td>\r\n" +
                        "</tr><tr><td>" +
                        "</td></tr></tbody></table></div>\r\n" +
                        "</blockquote></div>Dette var da bra da<br>Hilsen Test<br>\r\n";
        String filteredContents = contentFilter.filterContent(realContentsFromOutlookMail, "");
        assertNotNull(filteredContents);
        assertThat(filteredContents, is("Dette var da bra da\nHilsen Test")); // No bottom-post markers detected...
    }

    @Test
    public void testFilterContentWithRepliedPlainTextFromOutlook() {
        String realContentsFromArbitraryOutlookMail =
                REPLY +
                        '\n' +
                        '\n' +
                        "-----Opprinnelig melding-----\n" +
                        "Fra: Nagiosft [mailto:nagiosft@ops1.finntech.no]\n" +
                        "Sendt: 5. mars 2012 15:01\n" +
                        "Til: Test Testesen\n" +
                        "Emne: ** PROBLEM alert - Cassandra servers/Canary Page Views is CRITICAL **\n" +
                        "\n" +
                        "***** Nagios  *****\n" +
                        "\n" +
                        "Notification Type: PROBLEM\n" +
                        "\n" +
                        "Service: Canary Page Views\n" +
                        "Host: Cassandra servers\n" +
                        "Address: 127.0.0.1\n" +
                        "State: CRITICAL\n" +
                        "\n" +
                        "Date/Time: Mon Mar 5 15:00:35 CET 2012\n" +
                        "\n" +
                        "Additional Info\n:" +
                        "\n" +
                        "3615 secs since last update for cassandra01.finn.no-GAUGE-canary-g.rrd\n";
        String filteredContents = contentFilter.filterContent("", realContentsFromArbitraryOutlookMail);
        assertNotNull(filteredContents);
        assertThat(filteredContents, is(REPLY));
    }

    @Test
    public void testFilterContentWithReplyFromGmail() {
        String plainText = "Hm.\n" +
                "Har desverre ingen flere stoler!\n" +
                '\n' +
                "2013/1/3 Test Testesen <finnbruker@innboks.finn.no>\n" +
                '\n' +
                "> Har du ikke noe annet å selge? Noe som er litt nyere og ikke så slitt?\n" +
                "> (svar fra gmail)\n" +
                ">\n" +
                ">\n" +
                "> 2013/1/3 Test Testesen2 <finnbruker@innboks.finn.no>\n" +
                ">\n" +
                ">> nei, nei - disse stolene er i prima kvalitet tatt i betraktning at de er\n" +
                ">> 50 år gamle. Kjøp dem!\n" +
                ">>\n" +
                ">\n" +
                '>';

        String html = "Hm. Har desverre ingen flere stoler!<br><br><div>2013/1/3 Test testesen <span><<a href=\"mailto:finnbruker@innboks.finn.no\" target=\"_blank\">finnbruker@innboks.finn.no</a>></span><br>\n" +
                "<blockquote><div>Har du ikke noe annet å selge? Noe som er litt nyere og ikke så slitt? (svar fra gmail)</div><div>\n" +
                "<br><br><div>2013/1/3 test testesen <span><<a href=\"mailto:finnbruker@innboks.finn.no\" target=\"_blank\">finnbruker@innboks.finn.no</a>></span><br>\n" +
                "<blockquote><div>nei, nei - disse stolene er i prima kvalitet tatt i betraktning at de er 50 år gamle. Kjøp dem!</div>\n" +
                "</blockquote></div><br></div>\n" +
                "</blockquote></div><br>\n";

        String expected = "Hm.\n" +
                "Har desverre ingen flere stoler!";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromGmail2() {
        String plainText = "Takk forinteressen i alle fall. Dette er svar fra gmail\n" +
                '\n' +
                "2013/1/3 Test Testesen <finnbruker@innboks.finn.no>\n" +
                '\n' +
                ">  Det var synd. Da får jeg kjøpe stoler på Ikea isteden? ****\n" +
                ">\n" +
                "> ** **\n" +
                ">\n" +
                "> Dette svaret er fra Outlook. ****\n" +
                ">\n" +
                "> ** **\n" +
                ">\n" +
                "> Hilsen Test Testesen****\n" +
                ">\n" +
                "> ** **\n" +
                ">\n" +
                "> *Fra:* Test Testesen2 [mailto:finnbruker@innboks.finn.no]\n" +
                "> *Sendt:* 3. januar 2013 09:56\n" +
                "> *Til:* Test Testesen\n" +
                "> *Emne:* Spisestuestoler i palisander og sort lær****\n" +
                ">\n" +
                "> ** **\n" +
                ">\n" +
                "> Ja, de gikk desverre til en søt jente fra byen****\n" +
                ">\n";

        String html = "Takk forinteressen i alle fall. Dette er svar fra gmail<br><br><div>2013/1/3 Test Testesen <span><<a href=\"mailto:finnbruker@innboks.finn.no\" target=\"_blank\">finnbruker@innboks.finn.no</a>></span><br>\n" +
                "<blockquote>\n" +
                '\n' +
                '\n' +
                '\n' +
                '\n' +
                '\n' +
                "<div>\n" +
                "<div>\n" +
                "<p>Det var synd. Da får jeg kjøpe stoler på Ikea isteden?\n" +
                "<u></u><u></u></span></p>\n" +
                "<p>Dette svaret er fra Outlook.\n" +
                "<u></u><u></u></span></p>\n" +
                "<p><span>Hilsen Test<u></u><u></u></span></p>\n" +
                "<div>\n" +
                "<div>\n" +
                "<p>Fra:</span></b><span> Test Testesen [mailto:<a href=\"mailto:finnbruker@innboks.finn.no\" target=\"_blank\">finnbruker@innboks.finn.no</a>]\n" +
                "<br>\n" +
                "<b>Sendt:</b> 3. januar 2013 09:56<br>\n" +
                "<b>Til:</b> Test Testesen<br>\n" +
                "<b>Emne:</b> Spisestuestoler i palisander og sort lær<u></u><u></u></span></p>\n" +
                "</div>\n" +
                "</div>\n" +
                "<p>Ja, de gikk desverre til en søt jente fra byen<u></u><u></u></p>\n" +
                "</div>\n" +
                "</div>\n" +
                '\n' +
                "</blockquote></div><br>\n";

        String expected = "Takk forinteressen i alle fall. Dette er svar fra gmail";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromGmail3() {
        String plainText = "Hm.\n" +
                "Har desverre ingen flere stoler!\n" +
                '\n' +
                "Den torsdag 13. februar 2014 skrev test <samtale-hokuspokus@innboks.finn.no>\n" +
                '\n' +
                "> Har du ikke noe annet å selge? Noe som er litt nyere og ikke så slitt?\n" +
                "> (svar fra gmail)\n" +
                ">\n" +
                ">\n" +
                "> Den mandag 13. mars 2012 skrev test <samtale-hokuspokus@innboks.finn.no>\n" +
                ">\n" +
                ">> nei, nei - disse stolene er i prima kvalitet tatt i betraktning at de er\n" +
                ">> 50 år gamle. Kjøp dem!\n" +
                ">>\n" +
                ">\n" +
                '>';

        String html = "Hm. Har desverre ingen flere stoler!<br><br><div>Den torsdag 13. februar 2014 skrev test <span><<a href=\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>></span><br>\n" +
                "<blockquote><div>Har du ikke noe annet å selge? Noe som er litt nyere og ikke så slitt? (svar fra gmail)</div><div>\n" +
                "<br><br><div>Den torsdag 13. februar 2014 skrev test <span><<a href=\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>></span><br>\n" +
                "<blockquote><div>nei, nei - disse stolene er i prima kvalitet tatt i betraktning at de er 50 år gamle. Kjøp dem!</div>\n" +
                "</blockquote></div><br></div>\n" +
                "</blockquote></div><br>\n";

        String expected = "Hm.\n" +
                "Har desverre ingen flere stoler!";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromGmail4() {
        String plainText = "Hm.\n" +
                "Har desverre ingen flere stoler!\n" +
                '\n' +
                "torsdag 19. desember 2013 skrev Test Testsen følgende:\n" +
                '\n' +
                "> Har du ikke noe annet å selge? Noe som er litt nyere og ikke så slitt?\n" +
                "> (svar fra gmail)\n" +
                ">\n" +
                ">\n" +
                "> Den mandag 13. mars 2012 skrev test <samtale-hokuspokus@innboks.finn.no>\n" +
                ">\n" +
                ">> nei, nei - disse stolene er i prima kvalitet tatt i betraktning at de er\n" +
                ">> 50 år gamle. Kjøp dem!\n" +
                ">>\n" +
                ">\n" +
                '>';

        String html = "Hm. Har desverre ingen flere stoler!<br><br><div>Den torsdag 13. februar 2014 skrev test <span><<a href=\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>></span><br>\n" +
                "<blockquote><div>Har du ikke noe annet å selge? Noe som er litt nyere og ikke så slitt? (svar fra gmail)</div><div>\n" +
                "<br><br><div>Den torsdag 13. februar 2014 skrev test <span><<a href=\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>></span><br>\n" +
                "<blockquote><div>nei, nei - disse stolene er i prima kvalitet tatt i betraktning at de er 50 år gamle. Kjøp dem!</div>\n" +
                "</blockquote></div><br></div>\n" +
                "</blockquote></div><br>\n";

        String expected = "Hm.\n" +
                "Har desverre ingen flere stoler!";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithRepliedPlainTextWithSignature() {
        // Shold remove signature that follows the "-- \n" signature marker convention.
        String plainText = '\n' +
                "Men jeg har andre ting til salgs... (fra webmail)\n" +
                '\n' +
                ">Hei!\n" +
                ">FINN.no har sendt deg en melding på vegne av Test Testesen Hei.\n" +
                ">\n" +
                ">Fin salong. Er den fremdeles til salgs?\n" +
                ">\n" +
                ">Hilsen\n" +
                ">Test Testesen\n" +
                ">\n" +
                ">Henvendelsen gjelder  http://www.finn.no/finn/object?finnkode=0 \n" +
                ">med finnkode 0 Stusser du over avsenders e-postadresse?\n" +
                ">For å redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse.\n" +
                ">Det fungerer fortsatt å trykke svar/reply i e-posten. Mer om maskert \n" +
                ">e-post: http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser/\n" +
                ">\n" +
                ">Med vennlig hilsen\n" +
                ">FINN.no AS\n" +
                '\n' +
                "-- \n" +
                "--- \n" +
                "Test Testesen2 | Adresse 0 | 0000 Poststed | Norway test@test.no | www.test.no\n" +
                "+47 995 62 205\n" +
                '\n';

        String filteredContents = contentFilter.filterContent("", plainText);

        assertThat(filteredContents, is("Men jeg har andre ting til salgs... (fra webmail)"));
    }

    @Test
    public void testFilterContentWithReplyFromOutlook() {
        String plainText = "Eller hun… Svart fra Outlook, toppostet.\n" +
                '\n' +
                "Fra: Test Testesen2 [mailto:finnbruker@innboks.finn.no]\n" +
                "Sendt: 3. januar 2013 10:00\n" +
                "Til: Test Testesen\n" +
                "Emne: test\n" +
                '\n' +
                "Hei!\n" +
                '\n' +
                "FINN.no har sendt deg en melding på vegne av Test Testesen2\n" +
                "Åss'n har Gud det'a?\n" +
                '\n' +
                "Hilsen\n" +
                "Test Testesen2\n" +
                '\n' +
                '\n' +
                "Henvendelsen gjelder annonse med FINN-kode 0<http://www.finn.no/finn/object?finnkode=0>\n" +
                "Stusser du over avsenders e-postadresse?\n" +
                '\n' +
                "For å redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse.\n" +
                "Det fungerer fortsatt å trykke svar/reply i e-posten. Mer om maskert e-post<http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser/>\n" +
                '\n' +
                '\n';

        String htmlText = "<html xmlns:v=\"urn:schemas-microsoft-com:vml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:w=\"urn:schemas-microsoft-com:office:word\" xmlns:m=\"http://schemas.microsoft.com/office/2004/12/omml\" xmlns=\"http://www.w3.org/TR/REC-html40\">\n" +
                "<head>\n" +
                "</head>\n" +
                "<body lang=\"NO-BOK\" link=\"blue\" vlink=\"purple\">\n" +
                "<div>\n" +
                "<p><span>Eller hun… Svart fra Outlook, toppostet.<o:p></o:p></span></p>\n" +
                "<p><span><o:p>&nbsp;</o:p></span></p>\n" +
                "<div>\n" +
                "<p><b><span>Fra:</span></b><span> Test Testesen2 [mailto:finnbruker@innboks.finn.no]\n" +
                "<br>\n" +
                "<b>Sendt:</b> 3. januar 2013 10:00<br>\n" +
                "<b>Til:</b> Test Testesen<br>\n" +
                "<b>Emne:</b> test<o:p></o:p></span></p>\n" +
                "</div>\n" +
                "<p><o:p>&nbsp;</o:p></p>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<h3><span>Hei!<o:p></o:p></span></h3>\n" +
                "<p>FINN.no har sendt deg en melding på vegne av Test Testesen2 <o:p></o:p></p>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<div>\n" +
                "<p>Åss'n har Gud det'a?<o:p></o:p></p>\n" +
                "</div>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p>Hilsen <br>\n" +
                "Test Testesen2<o:p></o:p></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<p>Henvendelsen gjelder <a href=\"http://www.finn.no/finn/object?finnkode=0\" target=\"_blank\">\n" +
                "annonse med FINN-kode 0</a> <o:p></o:p></p>\n" +
                "<p><strong>Stusser du over avsenders e-postadresse?</strong> <o:p>\n" +
                "</o:p></p>\n" +
                "<p>For å redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse.\n" +
                "<br>\n" +
                "Det fungerer fortsatt å trykke svar/reply i e-posten. <a href=\"http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser/\">\n" +
                "Mer om maskert e-post</a><o:p></o:p></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<p><span><o:p>&nbsp;</o:p></span></p>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>\n";

        String filteredContents = contentFilter.filterContent(htmlText, plainText);

        assertThat(filteredContents, is("Eller hun… Svart fra Outlook, toppostet."));
    }

    @Test
    public void testFilterContentWithReplyFromHotmail() {
        String plainText = "Svar fra Hotmail. Legger på litt ekstra formatert tekst også...\n" +
                '\n' +
                "En punktliste:\n" +
                "punkt1punkt2\n" +
                "Hilsen Test\n" +
                '\n' +
                "Date: Thu, 3 Jan 2013 14:15:09 +0100\n" +
                "From: finnbruker@innboks.finn.no\n" +
                "To: someone@hotmail.com\n" +
                "Subject: Kvalitetssalong i ull fra Brunstad, 2 + 3 seter sofa\n" +
                '\n' +
                "Er dette hotmail? \t\t \t   \t\t";

        String html = "<html>\n" +
                "<head>\n" +
                "</head>\n" +
                "<body class='hmmessage'><div>\n" +
                "Svar fra Hotmail. Legger på <font style=\"\" color=\"#FF0000\">litt</font> <i>ekstra</i> <b>formatert</b> <u>tekst</u> også...<br><br>En punktliste:<br><ul><li>punkt1</li><li>punkt2</li></ul><br><BR>Hilsen Test<br><BR><br><div><div></div><hr>Date: Thu, 3 Jan 2013 14:15:09 +0100<br>From: finnbruker@innboks.finn.no<br>To: henning_gjetanger@hotmail.com<br>Subject: Kvalitetssalong i ull fra Brunstad, 2 + 3 seter sofa<br><br>Er dette hotmail?</div> \t\t \t   \t\t  </div></body>\n" +
                "</html>";

        String expected = "Svar fra Hotmail. Legger på litt ekstra formatert tekst også...\n\nEn punktliste:\npunkt1punkt2\nHilsen Test";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents.replaceAll("  ", " "), is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromYahooMail() {
        String plainText = "Svar fra yahoo mail.\n" +
                '\n' +
                '\n' +
                '\n' +
                "--- Den tor 2013-01-03 skrev Test Testesen2 <finnbruker@innboks.finn.no>:\n" +
                '\n' +
                "Fra: Test Testesen <finnbruker@innboks.finn.no>\n" +
                "Emne: Kvalitetssalong i ull fra Brunstad, 2 + 3 seter sofa\n" +
                "Til: someone@yahoo.no\n" +
                "Dato: Torsdag 3. januar 2013 14.42\n" +
                '\n' +
                "Svar fra FINNboks...";

        String html = "<table><tr><td valign=3D\"=\n" +
                "top\" style=3D\"font: inherit;\">Svar fra yahoo mail.<br><br><br><br>--- Den <=\n" +
                "b>tor 2013-01-03 skrev Test Testesen2 <i>&lt;finnbruker@innboks.finn.no&gt;=\n" +
                "</i></b>:<br><blockquote style=3D\"border-left: 2px solid rgb(16, 16, 255); =\n" +
                "margin-left: 5px; padding-left: 5px;\"><br>Fra: Test Testesen2 &lt;finnbruke=\n" +
                "r@innboks.finn.no&gt;<br>Emne: Kvalitetssalong i ull fra Brunstad, 2 + 3 se=\n" +
                "ter sofa<br>Til: someone@yahoo.no<br>Dato: Torsdag 3. januar 2013=\n" +
                " 14.42<br><br><div><div>Svar fra FINNboks...</div>\n" +
                "</div></blockquote></td></tr></table>";

        String expected = "Svar fra yahoo mail.";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromGetMail() {
        String plainText = "Svar fra Get Mail\n" +
                '\n' +
                "----- Original melding -----\n" +
                "Fra: Test Testesen <finnbruker@innboks.finn.no>\n" +
                "Dato: Torsdag, Januar 3, 2013 14:55\n" +
                "Emne: test\n" +
                "Til: someone@getmail.no\n" +
                "> Svar fra meldingsboksen til Getmail. \n";

        String html = "";

        String expected = "Svar fra Get Mail";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromMe() {
        String plainText = "Dette er svaret mitt fra .me adressen.\n" +
                '\n' +
                "Den 04. jan 2013 kl. 00.50 skrev Test Testesen2 <finnbruker@innboks.finn.no" +
                "> f=C3=B8lgende:\n" +
                '\n' +
                "Heisann igjen! Kan du svare p=C3=A5 denne ogs=C3=A5 via mail?";

        String html = "<html><body><div>Dette er svaret mitt fra .me adressen.<br><br>Den 04. jan =\n" +
                "2013 kl. 00.50 skrev Test Testesen2 &lt;finnbruker@innboks.finn.no&gt; f=C3=\n" +
                "=B8lgende:<br><br></div><div><blockquote><div>Heisann igjen! Kan du svare p=C3=A5 denne ogs=C3=A5 via mail?</div></b=\n" +
                "lockquote></div></body></html>";

        String expected = "Dette er svaret mitt fra .me adressen.";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromICloud() {
        String plainText = "Svaret mitt fra icloud adressen. Er det din sofa?\n" +
                '\n' +
                "Den 04. jan 2013 kl. 00.50 skrev Test Testesen2 <finnbruker@innboks.finn.no" +
                "> f=C3=B8lgende:\n" +
                '\n' +
                "Heisann! Kan du svare p=C3=A5 denne via mail?";

        String html = "<html><body><div>Svaret mitt fra icloud adressen. Er det din sofa?<br><br>D=\n" +
                "en 04. jan 2013 kl. 00.50 skrev Test Testesen2 &lt;finnbruker@innboks.finn.=\n" +
                "no&gt; f=C3=B8lgende:<br><br></div><div><blockquote><div>Heisann! Kan du svare p=C3=A5 denne via mail?</div></blockq=\n" +
                "uote></div></body></html>";

        String expected = "Svaret mitt fra icloud adressen. Er det din sofa?";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromMailDotCom() {
        String plainText = "Svar framail.com  *webklient*.\n" +
                '\n' +
                "----- Original Message -----\n" +
                "From: Test Testesen2\n" +
                "Sent: 01/04/13 10:51 AM\n" +
                "To: test@mail.com\n" +
                "Subject: Kvalitetssalong i ull fra Brunstad, 2 + 3 seter sofa\n" +
                '\n' +
                " Svarer på mail.com melding...\n" +
                '\n';

        String html = "<span><span>Svar <st=\n" +
                "rike>fra</strike> <u>mail</u>.com <strong>webklient</strong>.<br />=20\n" +
                "<br />=20\n" +
                "<p>=20\n" +
                "=09=C2=A0</p>=20\n" +
                "<blockquote>=20\n" +
                "=09<p>=20\n" +
                "=09=09<span><span>--=\n" +
                "--- Original Message -----</span></span></p>=20\n" +
                "=09<p>=20\n" +
                "=09=09<span><span>Fr=\n" +
                "om: Test Testesen2</span></span></p>=20\n" +
                "=09<p>=20\n" +
                "=09=09<span><span>Se=\n" +
                "nt: 01/04/13 10:51 AM</span></span></p>=20\n" +
                "=09<p>=20\n" +
                "=09=09<span><span>To=\n" +
                ": someone@mail.com</span></span></p>=20\n" +
                "=09<p>=20\n" +
                "=09=09<span><span>Su=\n" +
                "bject: Kvalitetssalong i ull fra Brunstad, 2 + 3 seter sofa</span></span></=\n" +
                "p>=20\n" +
                "=09<br />=20\n" +
                "=09<div>=20\n" +
                "=09=09Svarer p=C3=A5 mail.com melding...</div>=20\n" +
                "</blockquote>=20\n" +
                "<p>=20\n" +
                "=09=C2=A0</p>=20\n" +
                "<br />=20\n" +
                "</span></span>\n" +
                '\n';

        String expected = "Svar framail.com  *webklient*.";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromPetoroDotNo() {
        String plainText = "Hei,\n" +
                "Det er best du ringer min svigerfar som er den egentlige selgeren.\n" +
                "Maskinen har fungert bra for ham og er helt i orden.\n" +
                "Han gjør bare en oppgradering til større/mer avansert utstyr, \"slår seg litt løs\" på sine gamle dager.\n" +
                "Det følger med ekstra skjær. Metabo har helt sikkert ekstradeler.\n" +
                "Bare ring ham på 00000000\n" +
                '\n' +
                "Vennlig hilsen/Best regards\n" +
                "[cid:image001.gif@01CDED94.54273210]\n" +
                '\n' +
                "________________________________\n" +
                '\n' +
                "test testesen\n" +
                '\n' +
                "Senior rådgiver\n" +
                '\n' +
                "Modne oljefelt\n" +
                '\n' +
                '\n' +
                "x AS\n" +
                '\n' +
                "x y 124\n" +
                '\n' +
                "Postboks 0 x\n" +
                '\n' +
                "0 x\n" +
                '\n' +
                '\n' +
                "Telefon\n" +
                '\n' +
                "Mobil\n" +
                '\n' +
                '\n' +
                "+0\n" +
                '\n' +
                "+0 +0\n" +
                '\n' +
                '\n' +
                '\n' +
                '\n' +
                "Telefon\n" +
                '\n' +
                "Telefaks\n" +
                '\n' +
                '\n' +
                "+47 0\n" +
                '\n' +
                "+47 0\n" +
                '\n' +
                '\n' +
                "someone@somewhere.no<mailto:someone@somewhere.no>\n" +
                '\n' +
                "www.somewhere.no<http://www.somewhere.no>\n" +
                '\n' +
                '\n' +
                '\n' +
                "From: test testesen [mailto:samtale-hokuspokus@innboks.finn.no]\n" +
                "Sent: 8. x 0000 09:13\n" +
                "To: test testesen\n" +
                "Subject: Kombinasjonshøvel med støvavsug\n" +
                '\n' +
                "Hei!\n" +
                '\n' +
                "FINN.no har sendt deg en melding på vegne av test testesen\n" +
                "Hei Morten, Fin, men noe sjelden kombohøvel du selger. HVa er din erfaring med den? Hvorfor kvitter du deg med den? Har du på noe tidspunkt hatt behov for ekstradeler, og distribuerer i så fall Metabo fortsatt disse?\n" +
                '\n' +
                "Hilsen\n" +
                "test testesen\n" +
                '\n' +
                '\n' +
                "Henvendelsen gjelder annonse med FINN-kode 0<http://www.finn.no/finn/object?finnkode=0>\n" +
                "Stusser du over avsenders e-postadresse?\n" +
                '\n' +
                "For å redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse.\n" +
                "Det fungerer fortsatt å trykke svar/reply i e-posten. Mer om maskert e-post<http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser/>\n" +
                '\n' +
                "HEi\n" +
                '\n' +
                "________________________________\n" +
                '\n' +
                "Denne e-post er beregnet for den person den er rettet til. Informasjonen i denne e-post kan\n" +
                "være konfidensiell. Enhver uautorisert bruk av informasjonen i denne sendingen er ulovlig.\n" +
                "Hvis De ikke er rett mottaker bes De underrette avsender og tilintetgjøre denne sending.\n" +
                '\n' +
                "This e-mail is intended for the above addressee only. The information contained in this\n" +
                "message may be confidential. Any unauthorized use is prohibited. If you are not the\n" +
                "addressee, please notify the sender immediately by return e-mail and delete this message.\n";

        String html = "<html>\n" +
                "<head>\n" +
                "</head>\n" +
                "<body lang=\"NO-BOK\" link=\"blue\" vlink=\"purple\">\n" +
                "<div>\n" +
                "<p><span>Hei,\n" +
                "</span></p>\n" +
                "<p><span>Det er best du ringer min svigerfar som er den egentlige selgeren.</span></p>\n" +
                "<p><span>Maskinen har fungert bra for ham og er helt i orden.\n" +
                "</span></p>\n" +
                "<p><span>Han gjør bare en oppgradering til større/mer avansert utstyr, &quot;slår seg litt løs&quot; på sine gamle dager.</span></p>\n" +
                "<p><span>Det følger med ekstra skjær. Metabo har helt sikkert ekstradeler.</span></p>\n" +
                "<p><span>Bare ring ham på 00000000</span></p>\n" +
                "<p><span>&nbsp;</span></p>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr style=\"height:36.75pt\">\n" +
                "<td>\n" +
                "<p><span>Vennlig hilsen/Best regards</span><span></span></p>\n" +
                "<p><span><img width=\"127\" height=\"38\" id=\"_x0000_i1026\" src=\"cid:image001.gif@01CDED94.54273210\"></span><span></span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<div><span>\n" +
                "<hr>\n" +
                "</span></div>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><b><span>test testesen</span></b></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>x</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>x oljefelt</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "</td>\n" +
                "<td></td>\n" +
                "<td>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><b><span>x AS</span></b></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>x x 0</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>Postboks 0 z</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>0 z</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>Telefon</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>Mobil</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "</td>\n" +
                "<td>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>&#43;00000000</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>&#43;47 &#0;0</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "</td>\n" +
                "<td>\n" +
                "<p><span>&nbsp;</span></p>\n" +
                "</td>\n" +
                "<td>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>Telefon</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>Telefaks</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "</td>\n" +
                "<td>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>&#43;47 000000</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><span>&#43;47 000000</span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p><b><span><a href=\"mailto:someone@somewhere.no\" title=\"Click to send email to Someone Somewhere\"><span>someone@somewhere.no</span></a></span></b><span></span></p>\n" +
                "</td>\n" +
                "<td></td>\n" +
                "<td>\n" +
                "<p><b><span><a href=\"http://www.somewhere.no\" title=\"\"><span>www.somewhere.no</span></a></span></b><span></span></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr height=\"0\">\n" +
                "<td></td>\n" +
                "<td></td>\n" +
                "<td></td>\n" +
                "<td></td>\n" +
                "<td></td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<p><span>&nbsp;</span></p>\n" +
                "<p><b><span>From:</span></b><span> Test Testesen [mailto:samtale-hokuspokus@innboks.finn.no]\n" +
                "<br>\n" +
                "<b>Sent:</b> 8. januar 2013 09:13<br>\n" +
                "<b>To:</b> test testesen<br>\n" +
                "<b>Subject:</b> Kombinasjonshøvel med støvavsug</span></p>\n" +
                "<p>&nbsp;</p>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<h3><span>Hei!</span></h3>\n" +
                "<p>FINN.no har sendt deg en melding på vegne av test testesen </p>\n" +
                "<table>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<div>\n" +
                "<p>Hei test testesen, Fin, men noe sjelden kombohøvel du selger. HVa er din erfaring med den? Hvorfor kvitter du deg med den? Har du på noe tidspunkt hatt behov for ekstradeler, og distribuerer i så fall Metabo fortsatt disse?</p>\n" +
                "</div>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<p>Hilsen <br>\n" +
                "test testesen</p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<p>Henvendelsen gjelder <a href=\"http://www.finn.no/finn/object?finnkode=0\" target=\"_blank\">\n" +
                "annonse med FINN-kode 0</a> </p>\n" +
                "<p><strong>Stusser du over avsenders e-postadresse?</strong> </p>\n" +
                "<p>For å redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse.\n" +
                "<br>\n" +
                "Det fungerer fortsatt å trykke svar/reply i e-posten. <a href=\"http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser/\">\n" +
                "Mer om maskert e-post</a></p>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<p><span>HEi</span><span></span></p>\n" +
                "</div>\n" +
                "<br>\n" +
                "<hr>\n" +
                "<font face=\"Arial\" color=\"Gray\" size=\"1\"><br>\n" +
                "Denne e-post er beregnet for den person den er rettet til. Informasjonen i denne e-post kan<br>\n" +
                "være konfidensiell. Enhver uautorisert bruk av informasjonen i denne sendingen er ulovlig.<br>\n" +
                "Hvis De ikke er rett mottaker bes De underrette avsender og tilintetgjøre denne sending.<br>\n" +
                "<br>\n" +
                "This e-mail is intended for the above addressee only. The information contained in this<br>\n" +
                "message may be confidential. Any unauthorized use is prohibited. If you are not the<br>\n" +
                "addressee, please notify the sender immediately by return e-mail and delete this message.<br>\n" +
                "</font>\n" +
                "</body>\n" +
                "</html>\n";

        String expected = "Hei,\n" +
                "Det er best du ringer min svigerfar som er den egentlige selgeren.\n" +
                "Maskinen har fungert bra for ham og er helt i orden.\n" +
                "Han gjør bare en oppgradering til større/mer avansert utstyr, \"slår seg litt løs\" på sine gamle dager.\n" +
                "Det følger med ekstra skjær. Metabo har helt sikkert ekstradeler.\n" +
                "Bare ring ham på 00000000\n\n" +
                "Vennlig hilsen/Best regards\n" +
                "[cid:image001.gif@01CDED94.54273210]";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithReplyFromTomas() {
        String plainText = "Hei\n" +
                "Den 8. jan. 2013 08:42 skrev \"Test Testesen2\" <" +
                "samtale-hokuspokus@innboks.finn.no> f=F8lgende:\n" +
                '\n' +
                "> XX\n" +
                ">\n" +
                '\n';

        String html = "<p dir=3D\"ltr\">Hei</p>\n" +
                "<div>Den 8. jan. 2013 08:42 skrev &quot;Test Testese=\n" +
                "n&quot; &lt;<a href=3D\"mailto:samtale-hokuspokus@inn=\n" +
                "boks.finn.no\">samtale-hokuspokus@innboks.finn.no</a>=\n" +
                "&gt; f=F8lgende:<br type=3D\"attribution\">\n" +
                "<blockquote class=3D\"gmail_quote\" style=3D\"margin:0 0 0 .8ex;border-left:1p=\n" +
                "x #ccc solid;padding-left:1ex\"><div>XX</div>\n" +
                "</blockquote></div>\n" +
                '\n';

        String expected = "Hei";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterWithRealConversatonFromPerson() {
        String plainText = "Test2,\n" +
                '\n' +
                "Takk for info.\n" +
                '\n' +
                "Test\n" +
                '\n' +
                "2013/1/17 Test2 Testesen2 <\n" +
                "samtale-hokuspokus@innboks.finn.no>\n" +
                '\n' +
                "> Hei Test. Sagen er i utgangspunktet solgt. Jeg har fått oppgjør, men den\n" +
                "> er ikke hentet. Jeg har latt den stå på Finn i påvente av at den blir\n" +
                "> hentet, eller eventuelt om salget blir kansellert. Jeg skal gi deg\n" +
                "> tilbakemelding dersom dette blir aktuelt. Hilsen Test2\n" +
                ">\n" +
                "> 17. jan. 2013 kl. 15:37 skrev Test Testesen <\n" +
                "> samtale-hokuspokus@innboks.finn.no>:\n" +
                ">\n" +
                "> Hei!\n" +
                ">\n" +
                "> FINN.no har sendt deg en melding på vegne av Test Testesen\n" +
                ">  Hei Test2, Noen spørsmål angående sagen din: - Hvor sterk er den (kw)? -\n" +
                "> Ruller bord pent (oljet, gode kulelagre?) - Fungerer 45 grad tilt\n" +
                "> tilfredsstillende (lett å snurre inn/ut)? - Har jernbordet noen kosmetiske\n" +
                "> feil av noe slag (sveiser, hull, etc)? - Har du brukt sagen selv, har den\n" +
                "> fungert greit? Hører fra deg!\n" +
                ">  Hilsen\n" +
                "> Test Testesen\n" +
                ">\n" +
                "> Henvendelsen gjelder annonse med FINN-kode 0<http://www.finn.no/finn/object?finnkode=0>\n" +
                ">\n" +
                "> *Stusser du over avsenders e-postadresse?*\n" +
                ">\n" +
                "> For å redusere muligheten for spam ser verken du eller mottaker hverandres\n" +
                "> e-postadresse.\n" +
                "> Det fungerer fortsatt å trykke svar/reply i e-posten. Mer om falske\n" +
                "> e-poster <http://ks.finn.no/trygg-pa-finn/falske-eposter>\n" +
                ">\n" +
                ">\n" +
                '>';

        String html = "Test2,<br><br>Takk for info.<br><br>Test<br><br><div>2013/1/17 Test2 Testesen2 <span><<a href=\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>></span><br>\n" +
                "<blockquote><div>Hei Test. Sagen er i utgangspunktet solgt. Jeg har fått oppgjør, men den er ikke hentet. Jeg har latt den stå på Finn i påvente av at den blir hentet, eller eventuelt om salget blir kansellert. Jeg skal gi deg tilbakemelding dersom dette blir aktuelt. Hilsen Egil<div>\n" +
                "<br><div><div>17. jan. 2013 kl. 15:37 skrev Test Testesen <<a href=\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>>:</div>\n" +
                "<br><blockquote><table><tbody><tr><td>\n" +
                "    <h3>Hei!</h3><p>\n" +
                "    <a href=\"http://FINN.no\" target=\"_blank\">FINN.no</a> har sendt deg en melding på vegne av\n" +
                "                    Test Testesen\n" +
                "                </p>\n" +
                "    <table>\n" +
                "        <tbody><tr><td>\n" +
                "        <div>Hei Test2,\n" +
                "Noen spørsmål angående sagen din:\n" +
                "- Hvor sterk er den (kw)?\n" +
                "- Ruller bord pent (oljet, gode kulelagre?)\n" +
                "- Fungerer 45 grad tilt tilfredsstillende (lett å snurre inn/ut)?\n" +
                "- Har jernbordet noen kosmetiske feil av noe slag (sveiser, hull, etc)?\n" +
                "- Har du brukt sagen selv, har den fungert greit?\n" +
                "Hører fra deg!</div>\n" +
                "    </td></tr>\n" +
                "            <tr><td>\n" +
                "        Hilsen <br>Test Testesen</td></tr>\n" +
                "            </tbody></table><p>\n" +
                "            Henvendelsen gjelder  <a href=\"http://www.finn.no/finn/object?finnkode=0\" target=\"_blank\"> annonse med FINN-kode 39148319</a> \n" +
                "                                    </p><div><br></div>\n" +
                "    <b>Stusser du over avsenders e-postadresse?</b><p>For å redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse. <br>\n" +
                "    Det fungerer fortsatt å trykke svar/reply i e-posten. <a href=\"http://ks.finn.no/trygg-pa-finn/falske-eposter\" target=\"_blank\">Mer om falske e-poster</a></p>\n" +
                "</td></tr></tbody></table>\n" +
                "</blockquote></div><br></div></div></blockquote></div><br>";

        String expected = "Test2,\n\nTakk for info.\n\nTest";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterWithRealConversatonFromPerson2() {
        String plainText = "Hei.\n" +
                "Jeg er her vanligvis til 16.30, men til uka kan jeg være litt lenger på tirsdag eller torsdag. Mandag kan jeg være her til ca 17.00.\n" +
                '\n' +
                "Med vennlig hilsen\n" +
                "Test Testesen\n" +
                "x\n" +
                "Tlf: 0\n" +
                "Mobil: 0\n" +
                "www.x.no<http://www.x.no>\n" +
                "x\n" +
                "x!\n" +
                '\n' +
                "Fra: Test Testesen [mailto:samtale-hokuspokus@innboks.finn.no]\n" +
                "Sendt: 17. januar 2013 21:42\n" +
                "Til: Test Testesen\n" +
                "Emne: Justersag\n" +
                '\n' +
                "Rune, All respekt for plassmangel - det er jo en plasskrevende sag. Ser du jobber på kultursenteret - dersom jeg ønsker å ta en titt på sagen, når har du anledning til å møte meg? Jeg kan i utgangspunktet kun etter vanlig arbeidstid, og kjører inn fra Oslo (mtp mulig rush på ettermiddagen) Hører fra deg!\n";

        String html = "<html xmlns:v=\"urn:schemas-microsoft-com:vml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:w=\"urn:schemas-microsoft-com:office:word\" xmlns:m=\"http://schemas.microsoft.com/office/2004/12/omml\" xmlns=\"http://www.w3.org/TR/REC-html40\">\n" +
                "<head>\n" +
                "</head>\n" +
                "<body lang=\"NO-BOK\" link=\"blue\" vlink=\"purple\">\n" +
                "<div>\n" +
                "<p>Hei.<o:p></o:p></span></p>\n" +
                "<p>Jeg er her vanligvis til 16.30, men til uka kan jeg være litt lenger på tirsdag eller torsdag. Mandag kan jeg være her til ca 17.00.\n" +
                "<o:p></o:p></span></p>\n" +
                "<div>\n" +
                "<p>\n" +
                "<b><span>Med vennlig hilsen<br>\n" +
                "test testesen<br>\n" +
                "x</span></b><b><span><o:p></o:p></span></b></p>\n" +
                "<p>\n" +
                "<span>Tlf: 00000000<br>\n" +
                "Mobil: 00000000<br>\n" +
                "<a href=\"http://www.x.no\"><span>www.x.no</span></a>\n" +
                "</span><span><o:p></o:p></span></p>\n" +
                "<p><b><span>x<br>\n" +
                "</span></b><span>x!</span><span><o:p></o:p></span></p>\n" +
                "</div>\n" +
                "<p><span><o:p>&nbsp;</o:p></span></p>\n" +
                "<div>\n" +
                "<div>\n" +
                "<p><b><span>Fra:</span></b><span> test testesen [mailto:samtale-hokuspokus@innboks.finn.no]\n" +
                "<br>\n" +
                "<b>Sendt:</b> 17. januar 2013 21:42<br>\n" +
                "<b>Til:</b> Test Testesen<br>\n" +
                "<b>Emne:</b> Justersag<o:p></o:p></span></p>\n" +
                "</div>\n" +
                "</div>\n" +
                "<p><o:p>&nbsp;</o:p></p>\n" +
                "<p>Test Testesen, All respekt for plassmangel - det er jo en plasskrevende sag. Ser du jobber på kultursenteret - dersom jeg ønsker å ta en titt på sagen, når har du anledning til å møte meg? Jeg kan i utgangspunktet kun etter vanlig arbeidstid, og\n" +
                " kjører inn fra Oslo (mtp mulig rush på ettermiddagen) Hører fra deg!<o:p></o:p></p>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>\n";

        String expected = "Hei.\nJeg er her vanligvis til 16.30, men til uka kan jeg være litt lenger på tirsdag eller torsdag. Mandag kan jeg være her til ca 17.00.\nMed vennlig hilsen\nTest Testesen\nx";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testAppleMail_2_1283() {
        String plainText = "S=E5 bra. Den er demontert, ikke sant? Forstod det slik ut fra bildene.=20\n" +
                "I s=E5 tilfelle tar vi den:) N=E5r passer det =E5 hente?\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "Den 3. feb. 2013 kl. 19:27 skrev Test Testesen:\n" +
                '\n' +
                "> Hei. hylla er ikke solgt. Den er i helt fin stand. overflatene er fine.\n";

        String expected = "S=E5 bra. Den er demontert, ikke sant? Forstod det slik ut fra bildene.=20\n" +
                "I s=E5 tilfelle tar vi den:) N=E5r passer det =E5 hente?\n\n" +
                "Test Testesen";

        String filteredContents = contentFilter.filterContent("", plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testAppleMail_2_1283__2() {
        String plainText = "Den er demontert og klar for henting. Passer n=E5 ikveld?\n" +
                '\n' +
                '\n' +
                "2013/2/3 Test Testesen <\n" +
                "samtale-hokuspokus@innboks.finn.no>\n" +
                '\n' +
                "> S=E5 bra. Den er demontert, ikke sant? Forstod det slik ut fra bildene.\n" +
                "> I s=E5 tilfelle tar vi den:) N=E5r passer det =E5 hente?\n" +
                ">\n" +
                "> Test Testesen\n" +
                ">\n" +
                ">\n" +
                "> Den 3. feb. 2013 kl. 19:27 skrev Tomas H=E5heim Mortensen:\n" +
                ">\n" +
                "> > Hei. hylla er ikke solgt. Den er i helt fin stand. overflatene er fine.\n" +
                ">\n" +
                ">\n";

        String html = "<div>Den er demontert og klar for henting. Passer n=E5 ikveld?<=\n" +
                "br></div><div><br><br><div>2013=\n" +
                "/2/3 Test Testesen <span>&lt;<a href=3D\"mailto:finn-968becb7=\n" +
                "-5631-48a8-989f-c9914e98fd14@innboks.finn.no\" target=3D\"_blank\">finn-968bec=\n" +
                "b7-5631-48a8-989f-c9914e98fd14@innboks.finn.no</a>&gt;</span><br>\n" +
                "<blockquote class=3D\"gmail_quote\" style=3D\"margin:0 0 0 .8ex;border-left:1p=\n" +
                "x #ccc solid;padding-left:1ex\">S=E5 bra. Den er demontert, ikke sant? Forst=\n" +
                "od det slik ut fra bildene.<br>\n" +
                "I s=E5 tilfelle tar vi den:) N=E5r passer det =E5 hente?<br>\n" +
                "<br>\n" +
                "Test Testesen<br>\n" +
                "<br>\n" +
                "<br>\n" +
                "Den 3. feb. 2013 kl. 19:27 skrev Test Testesen:<br>\n" +
                "<br>\n" +
                "&gt; Hei. hylla er ikke solgt. Den er i helt fin stand. overflatene er fine=\n" +
                ".<br>\n" +
                "<br>\n" +
                "</blockquote></div><br></div>";

        String expected = "Den er demontert og klar for henting. Passer n=E5 ikveld?";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithOnlyHtml() {
        String plainText = null;

        String html = "<html>Hei";

        String expected = "Hei";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterContentWithOnlyPlaintext() {
        String plainText = "Hei";

        String html = null;

        String expected = "Hei";

        String filteredContents = contentFilter.filterContent(html, plainText);

        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testFilterSubject() {
        assertThat(contentFilter.filterSubject("Re: SV: Re: Fw: FWD: Vs: Original subject"), is("Original subject"));
    }


    @Test
    public void testFilterSubject_MissingSubject_FromRealMail() throws MessagingException {
        String messageSource = "Received: from mx2.schibsted-it.no (mx2.schibsted-it.no [mx2.schibsted-it.no/80.91.34.67])\n" +
                "        by FINN.no inbound SMTP relay with SMTP;\n" +
                "        ti, 15 01 2013 11:07:15 +0100 (CET)\n" +
                "X-Spam-Status: No\n" +
                "X-MediaNorge-MailScanner-From: someone@yahoo.com\n" +
                "X-MediaNorge-MailScanner-SpamScore:  1.59\n" +
                "X-MediaNorge-MailScanner-SpamCheck: not spam, SpamAssassin (not cached,\n" +
                "\tscore=1.591, required 6, BAYES_50 1.50, DKIM_SIGNED 0.10,\n" +
                "\tDKIM_VALID -0.10, DKIM_VALID_AU -0.10, FREEMAIL_FROM 0.00,\n" +
                "\tHTML_MESSAGE 0.10, MISSING_SUBJECT 0.10, RCVD_IN_DNSWL_NONE -0.00,\n" +
                "\tT_RP_MATCHES_RCVD -0.01)\n" +
                "X-MediaNorge-MailScanner: Found to be clean\n" +
                "X-MediaNorge-MailScanner-ID: 8A67413CB1F.A834F\n" +
                "X-Greylist: domain auto-whitelisted by SQLgrey-1.8.0-rc2\n" +
                "Received: from nm27-vm0.bullet.mail.bf1.yahoo.com (nm27-vm0.bullet.mail.bf1.yahoo.com [98.139.213.139])\n" +
                "\t(using TLSv1 with cipher DHE-RSA-AES256-SHA (256/256 bits))\n" +
                "\t(No client certificate requested)\n" +
                "\tby mx2.schibsted-it.no (Postfix) with ESMTPS id 8A67413CB1F\n" +
                "\tfor <samtale-hokuspokus@innboks.finn.no>; Tue, 15 Jan 2013 11:07:09 +0100 (CET)\n" +
                "Received: from [98.139.212.147] by nm27.bullet.mail.bf1.yahoo.com with NNFMP; 15 Jan 2013 10:07:07 -0000\n" +
                "Received: from [98.139.212.231] by tm4.bullet.mail.bf1.yahoo.com with NNFMP; 15 Jan 2013 10:07:06 -0000\n" +
                "Received: from [127.0.0.1] by omp1040.mail.bf1.yahoo.com with NNFMP; 15 Jan 2013 10:07:06 -0000\n" +
                "X-Yahoo-Newman-Property: ymail-3\n" +
                "X-Yahoo-Newman-Id: 703155.28959.bm@omp1040.mail.bf1.yahoo.com\n" +
                "Received: (qmail 88611 invoked by uid 60001); 15 Jan 2013 10:07:06 -0000\n" +
                "DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=yahoo.com; s=s1024; t=1358244426; bh=Q5DohXUrGYAC7OaDt2b1h15yr+eG4ZJwtJs4O2+OO7U=; h=X-YMail-OSG:Received:X-Rocket-MIMEInfo:X-Mailer:Message-ID:Date:From:Reply-To:To:MIME-Version:Content-Type; b=sE47oQqkNvjTnuSFx53ewF5/k0GvytNczH2hIdA77+1Npo3Rs2+OMCKonFrSd4DIb0X+JORd2WcSb07gFIf4PqxlNB37fJrQVdQ/K7QDw6DH3InkzdxwE7BxNEmfeBe9Oq2oiej12JiakNGTVJeou89rUAF6TVL81qm5M+z7EpE=\n" +
                "DomainKey-Signature:a=rsa-sha1; q=dns; c=nofws;\n" +
                "  s=s1024; d=yahoo.com;\n" +
                "  h=X-YMail-OSG:Received:X-Rocket-MIMEInfo:X-Mailer:Message-ID:Date:From:Reply-To:To:MIME-Version:Content-Type;\n" +
                "  b=IlCSzp2h15K5HMVbDIpX+w/4QQRW1gJ8Zb3R6m6S+2L74ZmI+NKGZtrBEDanVUcf+JX8s1FWeEEdFjGdgoPQHU7B7jJEVeXrryyUevMgtoO/IKVVKCDzpSOR2s7HaDUr22KDzMZP0RMWzQnV/qobDwhScHLKWHJeCXnmxyH5WuA=;\n" +
                "X-YMail-OSG: ot6MjXUVM1mZK_oOHoR1zdjeDm9ChhAe5dAs.BTp3biZlCy\n" +
                " Dk_zJw31jnVPHl.21SahY3N0Wn9AFoD4HdCap3hgyYHjINbhi.TWPQhqRsSu\n" +
                " qrMdTU8kdhAXTGpM331dnz7kNfJAhp1cprYBz6cNiR.Tse6aN.nLTLuxjMjc\n" +
                " 1GHVjXArrNy.iKtKxGN87fAPb7ovUBnHWVcec7tBb64YS0wYICwEGLg_dEvk\n" +
                " MKxme12lZ.JjbXNGS3X12tRxpMAlW5qLkjr81U.cws_1M5V7uUaLosEOOGIY\n" +
                " 6vbPs24DIUxN9SvC_fFOJh3ZUn4eMqsB.o7bR9_2r69pNgc4SJqL7LyhOnB7\n" +
                " NfOIpVaGBwKHAmYAH8_BwCy5VVqu8TQlqafvKmEFxEdkKHBkKgEvkQ0bsywp\n" +
                " MMchi.9j33kjNCmHe5f9__RJjvl.6McAljW7yS8r76IJwIRccOro4fVyROwK\n" +
                " eIm8nXSElxg7a6f.s\n" +
                "Received: from [84.16.198.34] by web160506.mail.bf1.yahoo.com via HTTP; Tue, 15 Jan 2013 02:07:06 PST\n" +
                "X-Rocket-MIMEInfo: 001.001,aGVpIGR1IGJvciBkdSBsYW5nIGZyYSBzdGrDuHJkYWw_Pz8BMAEBAQE-\n" +
                "X-Mailer: YahooMailWebService/0.8.130.494\n" +
                "Message-ID: <1358244426.25518.YahooMailNeo@web160506.mail.bf1.yahoo.com>\n" +
                "Date: Tue, 15 Jan 2013 02:07:06 -0800 (PST)\n" +
                "From: Test Testesen <someone@yahoo.com>\n" +
                "Reply-To: Test Testesen <someone@yahoo.com>\n" +
                "To: \"samtale-hokuspokus@innboks.finn.no\" <samtale-hokuspokus@innboks.finn.no>\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: multipart/alternative; boundary=\"1803139914-1746407106-1358244426=:25518\"\n" +
                "X-samtale-hokuspokus@innboks.finn.no\n" +
                '\n' +
                "--1803139914-1746407106-1358244426=:25518\n" +
                "Content-Type: text/plain; charset=iso-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "hei du bor du lang fra stj=F8rdal???\n" +
                "--1803139914-1746407106-1358244426=:25518\n" +
                "Content-Type: text/html; charset=iso-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "<html><body><div style=3D\"color:#000; background-color:#fff; font-family:bo=\n" +
                "okman old style, new york, times, serif;font-size:18pt\"><div>hei du bor du =\n" +
                "lang fra stj=F8rdal???</div></div></body></html>\n" +
                "--1803139914-1746407106-1358244426=:25518--";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        assertThat(contentFilter.filterSubject(message.getSubject()), is(""));
    }

    @Test
    public void testFilterSubject_AddedEmptySubject_FromRealMail() throws MessagingException {
        String messageSource = "Received: from mx2.schibsted-it.no (mx2.schibsted-it.no [mx2.schibsted-it.no/80.91.34.67])\n" +
                "        by FINN.no inbound SMTP relay with SMTP;\n" +
                "        ti, 15 01 2013 11:07:15 +0100 (CET)\n" +
                "X-Spam-Status: No\n" +
                "X-MediaNorge-MailScanner-From: someone@yahoo.com\n" +
                "X-MediaNorge-MailScanner-SpamScore:  1.59\n" +
                "X-MediaNorge-MailScanner-SpamCheck: not spam, SpamAssassin (not cached,\n" +
                "\tscore=1.591, required 6, BAYES_50 1.50, DKIM_SIGNED 0.10,\n" +
                "\tDKIM_VALID -0.10, DKIM_VALID_AU -0.10, FREEMAIL_FROM 0.00,\n" +
                "\tHTML_MESSAGE 0.10, MISSING_SUBJECT 0.10, RCVD_IN_DNSWL_NONE -0.00,\n" +
                "\tT_RP_MATCHES_RCVD -0.01)\n" +
                "X-MediaNorge-MailScanner: Found to be clean\n" +
                "X-MediaNorge-MailScanner-ID: 8A67413CB1F.A834F\n" +
                "X-Greylist: domain auto-whitelisted by SQLgrey-1.8.0-rc2\n" +
                "Received: from nm27-vm0.bullet.mail.bf1.yahoo.com (nm27-vm0.bullet.mail.bf1.yahoo.com [98.139.213.139])\n" +
                "\t(using TLSv1 with cipher DHE-RSA-AES256-SHA (256/256 bits))\n" +
                "\t(No client certificate requested)\n" +
                "\tby mx2.schibsted-it.no (Postfix) with ESMTPS id 8A67413CB1F\n" +
                "\tfor <samtale-hokuspokus@innboks.finn.no>; Tue, 15 Jan 2013 11:07:09 +0100 (CET)\n" +
                "Received: from [98.139.212.147] by nm27.bullet.mail.bf1.yahoo.com with NNFMP; 15 Jan 2013 10:07:07 -0000\n" +
                "Received: from [98.139.212.231] by tm4.bullet.mail.bf1.yahoo.com with NNFMP; 15 Jan 2013 10:07:06 -0000\n" +
                "Received: from [127.0.0.1] by omp1040.mail.bf1.yahoo.com with NNFMP; 15 Jan 2013 10:07:06 -0000\n" +
                "X-Yahoo-Newman-Property: ymail-3\n" +
                "X-Yahoo-Newman-Id: 703155.28959.bm@omp1040.mail.bf1.yahoo.com\n" +
                "Received: (qmail 88611 invoked by uid 60001); 15 Jan 2013 10:07:06 -0000\n" +
                "DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=yahoo.com; s=s1024; t=1358244426; bh=Q5DohXUrGYAC7OaDt2b1h15yr+eG4ZJwtJs4O2+OO7U=; h=X-YMail-OSG:Received:X-Rocket-MIMEInfo:X-Mailer:Message-ID:Date:From:Reply-To:To:MIME-Version:Content-Type; b=sE47oQqkNvjTnuSFx53ewF5/k0GvytNczH2hIdA77+1Npo3Rs2+OMCKonFrSd4DIb0X+JORd2WcSb07gFIf4PqxlNB37fJrQVdQ/K7QDw6DH3InkzdxwE7BxNEmfeBe9Oq2oiej12JiakNGTVJeou89rUAF6TVL81qm5M+z7EpE=\n" +
                "DomainKey-Signature:a=rsa-sha1; q=dns; c=nofws;\n" +
                "  s=s1024; d=yahoo.com;\n" +
                "  h=X-YMail-OSG:Received:X-Rocket-MIMEInfo:X-Mailer:Message-ID:Date:From:Reply-To:To:MIME-Version:Content-Type;\n" +
                "  b=IlCSzp2h15K5HMVbDIpX+w/4QQRW1gJ8Zb3R6m6S+2L74ZmI+NKGZtrBEDanVUcf+JX8s1FWeEEdFjGdgoPQHU7B7jJEVeXrryyUevMgtoO/IKVVKCDzpSOR2s7HaDUr22KDzMZP0RMWzQnV/qobDwhScHLKWHJeCXnmxyH5WuA=;\n" +
                "X-YMail-OSG: ot6MjXUVM1mZK_oOHoR1zdjeDm9ChhAe5dAs.BTp3biZlCy\n" +
                " Dk_zJw31jnVPHl.21SahY3N0Wn9AFoD4HdCap3hgyYHjINbhi.TWPQhqRsSu\n" +
                " qrMdTU8kdhAXTGpM331dnz7kNfJAhp1cprYBz6cNiR.Tse6aN.nLTLuxjMjc\n" +
                " 1GHVjXArrNy.iKtKxGN87fAPb7ovUBnHWVcec7tBb64YS0wYICwEGLg_dEvk\n" +
                " MKxme12lZ.JjbXNGS3X12tRxpMAlW5qLkjr81U.cws_1M5V7uUaLosEOOGIY\n" +
                " 6vbPs24DIUxN9SvC_fFOJh3ZUn4eMqsB.o7bR9_2r69pNgc4SJqL7LyhOnB7\n" +
                " NfOIpVaGBwKHAmYAH8_BwCy5VVqu8TQlqafvKmEFxEdkKHBkKgEvkQ0bsywp\n" +
                " MMchi.9j33kjNCmHe5f9__RJjvl.6McAljW7yS8r76IJwIRccOro4fVyROwK\n" +
                " eIm8nXSElxg7a6f.s\n" +
                "Received: from [84.16.198.34] by web160506.mail.bf1.yahoo.com via HTTP; Tue, 15 Jan 2013 02:07:06 PST\n" +
                "X-Rocket-MIMEInfo: 001.001,aGVpIGR1IGJvciBkdSBsYW5nIGZyYSBzdGrDuHJkYWw_Pz8BMAEBAQE-\n" +
                "X-Mailer: YahooMailWebService/0.8.130.494\n" +
                "Message-ID: <1358244426.25518.YahooMailNeo@web160506.mail.bf1.yahoo.com>\n" +
                "Date: Tue, 15 Jan 2013 02:07:06 -0800 (PST)\n" +
                "From: Test Testesen <someone@yahoo.com>\n" +
                "Reply-To: Test Testesen <someone@yahoo.com>\n" +
                "To: \"samtale-hokuspokus@innboks.finn.no\" <samtale-hokuspokus@innboks.finn.no>\n" +
                "Subject: \n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: multipart/alternative; boundary=\"1803139914-1746407106-1358244426=:25518\"\n" +
                "X-samtale-hokuspokus@innboks.finn.no\n" +
                '\n' +
                "--1803139914-1746407106-1358244426=:25518\n" +
                "Content-Type: text/plain; charset=iso-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "hei du bor du lang fra stj=F8rdal???\n" +
                "--1803139914-1746407106-1358244426=:25518\n" +
                "Content-Type: text/html; charset=iso-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "<html><body><div style=3D\"color:#000; background-color:#fff; font-family:bo=\n" +
                "okman old style, new york, times, serif;font-size:18pt\"><div>hei du bor du =\n" +
                "lang fra stj=F8rdal???</div></div></body></html>\n" +
                "--1803139914-1746407106-1358244426=:25518--";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        assertThat(contentFilter.filterSubject(message.getSubject()), is(""));
    }

    @Test
    public void testFilterContents_ShouldNotTruncate_FromRealEmployeeMail() throws MessagingException, IOException {
        String messageSource = "MIME-Version: 1.0\n" +
                "Received: by 10.204.29.19 with HTTP; Tue, 26 Feb 2013 09:08:53 -0800 (PST)\n" +
                "In-Reply-To: <35202399.172906726.1361897040895.JavaMail.noreply@finn.no>\n" +
                "References: <35202399.172906726.1361897040895.JavaMail.noreply@finn.no>\n" +
                "Date: Tue, 26 Feb 2013 18:08:53 +0100\n" +
                "Delivered-To: someone@gmail.com\n" +
                "Message-ID: <CA+jH_FUb-0J+35hESsUPywJZoan_7uVz47oaEkxUeQKR6xV26A@mail.gmail.com>\n" +
                "Subject: =?ISO-8859-1?Q?Re=3Adings?=\n" +
                "From: Test Testesen <someone@gmail.com>\n" +
                "To: Someone <samtale-hokuspokus@innboks.finn.no>\n" +
                "Content-Type: multipart/alternative; boundary=0015174c33ce4813a504d6a3b620 \n" +
                '\n' +
                "--0015174c33ce4813a504d6a3b620\n" +
                "Content-Type: text/plain; charset=ISO-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "Hei Test,\n" +
                '\n' +
                "Ja, de er fortsatt til salgs. Vi kjopte de fordi sonnen var likte sk=\n" +
                "oyting,\n" +
                "de skal kunne brukes til begge deler. Se for eksempel\n" +
                "http://madshus.com/boots/nano-jrr-1213?language=3Dno\n" +
                '\n' +
                "Med vennlig hilsen\n" +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "2013/2/26 test <samtale-hokuspokus@innboks.finn.no>\n" +
                '\n' +
                "> Hei!\n" +
                "> \n" +
                "> FINN.no har sendt deg en melding p=E5 vegne av Test\n" +
                ">  Hei. Er skisko fortsatt til salgs? Er de gode til kombi-bruk sk=F8yting =\n" +
                "og\n" +
                "> klassisk?\n" +
                ">  Hilsen\n" +
                "> Test\n" +
                "> \n" +
                "> Henvendelsen gjelder annonsen med FINN-kode 0<http://www.finn.no/f=\n" +
                "inn/object?finnkode=3D0> og referanse 0.0.\n" +
                "> \n" +
                "> *Stusser du over avsenders e-postadresse?*\n" +
                "> \n" +
                "> For =E5 redusere muligheten for spam ser verken du eller mottaker hverand=\n" +
                "res\n" +
                "> e-postadresse.\n" +
                "> Det fungerer fortsatt =E5 trykke svar/reply i e-posten. Mer om maskert\n" +
                "> e-post <http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser>\n" +
                "> \n" +
                '\n' +
                "--0015174c33ce4813a504d6a3b620\n" +
                "Content-Type: text/html; charset=ISO-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "Hei Siri,=A0<div><br></div><div>Ja, de er fortsatt til salgs. Vi kj=F8pte d=\n" +
                "e fordi s=F8nnen v=E5r likte sk=F8yting, de skal kunne brukes til begge del=\n" +
                "er. Se for eksempel=A0<a href=3D\"http://x.com/x/x-jrr-1213?lan=\n" +
                "guage=3Dno\">http://x.com/boots/nano-jrr-1213?language=3Dno</a></div>\n" +
                "<div><br></div><div>Med vennlig hilsen</div><div>Test Testesen</div><div=\n" +
                "><br><br><div>2013/2/26 Test <span>&lt;<a=\n" +
                "href=3D\"mailto:samtale-hokuspokus@innboks.finn.no\" =\n" +
                "target=3D\"_blank\">samtale-hokuspokus@innboks.finn.no=\n" +
                "</a>&gt;</span><br>\n" +
                "<blockquote class=3D\"gmail_quote\" style=3D\"margin:0 0 0 .8ex;border-left:1p=\n" +
                "x #ccc solid;padding-left:1ex\"><table><tbody><tr><td>\n" +
                "    <h3 style>Hei!</h3>\n" +
                "    <p>\n" +
                "    FINN.no har sendt deg en melding p=E5 vegne av\n" +
                "                    Test\n" +
                "                </p>\n" +
                "    <table style=3D\"border:1px dotted #999;width:450px\" cellpadding=3D\"0\" c=\n" +
                "ellspacing=3D\"0\" width=3D\"450px\">\n" +
                "        <tbody><tr><td style=3D\"background:#def;padding:8px\" width=3D\"450px=\n" +
                "\">\n" +
                "        <div>Hei. Er skisko fortsatt til salgs? Er de gode til kombi-bruk s=\n" +
                "k=F8yting og klassisk?</div>\n" +
                "    </td></tr>\n" +
                "            <tr><td>\n" +
                "        Hilsen <br>Test</td></tr>\n" +
                "            </tbody></table>\n" +
                "    <p>\n" +
                "            Henvendelsen gjelder  <a href=3D\"http://www.finn.no/finn/object=\n" +
                "?finnkode=3D0\" target=3D\"_blank\">annonsen med FINN-kode 39771024</a>=\n" +
                "=20\n" +
                "                    =A0og referanse 1360501884.074304.\n" +
                "                </p>\n" +
                "    <p>=\n" +
                "</p>\n" +
                "    <strong>Stusser du over avsenders e-postadresse?</strong>\n" +
                "    <p>For =E5 redusere muligheten for spam ser verken du eller mottaker hv=\n" +
                "erandres e-postadresse. <br>\n" +
                "    Det fungerer fortsatt =E5 trykke svar/reply i e-posten. <a href=3D\"http=\n" +
                "://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser\" target=3D\"_blank\">M=\n" +
                "er om maskert e-post</a></p>\n" +
                "</td></tr></tbody></table>\n" +
                "</blockquote></div><br></div>\n" +
                '\n' +
                "--0015174c33ce4813a504d6a3b620--\n";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        assertThat(contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain()), is("Hei Test,\n\n" +
                "Ja, de er fortsatt til salgs. Vi kjopte de fordi sonnen var likte skoyting,\nde skal kunne brukes til begge deler. " +
                "Se for eksempel\nhttp://madshus.com/boots/nano-jrr-1213?language=no\n\n" +
                "Med vennlig hilsen\n" +
                "Test Testesen"));
    }

    @Test
    public void testFilterWithRealConversatonFromPerson3() {
        // From http://www.inbox.lv/
        String htmlText = "<style type=\"text/css\">\n" +
                "/*<![CDATA[*/\n" +
                " blockquote.c5 {border-left: 1px solid #cccccc; margin: 0pt 0pt 0pt 0.8ex; padding-left: 1ex}\n" +
                " p.c4 {border-top: 1px dotted #000; margin-top: 8px; padding-top: 8px}\n" +
                " table.c3 {border: 1px dotted #999; width: 450px}\n" +
                " td.c2 {background: #DEF; padding: 8px}\n" +
                " h3.c1 {color: #000}\n" +
                "/*]]>*/\n" +
                "</style> Hei, Test Testesen !<br /><br />Fungerer helt bra. Virker ganske stille og treneger ikke mye lyd.<br /><br />Med hilsen,<br />Test<br /> <br /> <div>Cit�?jot <strong>Tor Harald <a href=\"http://mail.inbox.lv/horde/imp/compose.php?to=mailto%3asamtale-hokuspokus@innboks.finn.no></a></strong> :</div> <blockquote> <table> <tbody><tr> <td><h3 class=\"c1\">Hei!</h3> <p>FINN.no har sendt deg en melding pü vegne av Tor Harald</p> <table> <tbody><tr> <td><div>Hei, fungerer denne helt topp? Lager den mye lyd eller er den stillegüende?</div></td> </tr> <tr> <td>Hilsen<br /> Tor Harald</td> </tr> </tbody></table> <p>Henvendelsen gjelder <a target=\"_blank\" href=\"http://www.finn.no/finn/object?finnkode=0\">annonsen med FINN-kode 0</a></p> <p> </p> <strong>Stusser du over avsenders e-postadresse?</strong> <p>For ü redusere muligheten for spam ser verken du eller mottaker hverandres e-postadresse.<br /> Det fungerer fortsatt ü trykke svar/reply i e-posten. <a target=\"_blank\" href=\"http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser\">Mer om maskert e-post</a></p></td> </tr> </tbody></table> </blockquote>";

        String expected = "Hei, Test Testesen !\n\nFungerer helt bra. Virker ganske stille og treneger ikke mye lyd.\n\nMed hilsen,\nTest";

        String filteredContents = contentFilter.filterContent(htmlText, "");

        assertTrue(filteredContents.startsWith(expected));

    }

    @Test
    public void testFilterContent_ShouldKeepLinebreaks() throws MessagingException, IOException {
        assertThat(contentFilter.filterContent("Test<br>\nlinebreak", "Test\nlinebreak"), is("Test\nlinebreak"));
    }

    @Test
    public void testMessage_ShouldNotBeBlank() throws MessagingException, IOException {
        String messageSource = "                                                                                                                                                                                                                                                               \n" +
                "MIME-Version: 1.0\n" +
                "Received: by 10.50.36.234 with HTTP; Mon, 17 Jun 2013 06:12:31 -0700 (PDT)\n" +
                "In-Reply-To: <40362805.224312013.1371471647357.JavaMail.noreply@finn.no>\n" +
                "References: <40362805.224312013.1371471647357.JavaMail.noreply@finn.no>\n" +
                "Date: Mon, 17 Jun 2013 15:12:31 +0200\n" +
                "Delivered-To: someone@gmail.com\n" +
                "Message-ID: <CAHEF0d0Nfcjow_QbhgHv-Bk3W7t-uArL5HC_7F-ZsfV4jSaX9g@mail.gmail.com>\n" +
                "Subject: =?ISO-8859-1?Q?Re=3A_Retro_=2D_Tandberg_S=F8lvsuper_10_m=2Fh=F8yttalere?=\n" +
                "From: Test Testesen <someone@gmail.com>\n" +
                "To: FINN-bruker <samtale-hokuspokus@innboks.finn.no>\n" +
                "Content-Type: multipart/alternative; boundary=089e01183ec26859c504df595986\n" +
                '\n' +
                "--089e01183ec26859c504df595986\n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "Det har jeg.\n" +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14:20 17. juni 2013 skrev FINN-bruker <\n" +
                "samtale-hokuspokus@innboks.finn.no>:\n" +
                '\n' +
                ">          [image: FINN.no]                 Hei!\n" +
                ">\n" +
                "> FINN.no har sendt deg en melding p=EF=BF=BD vegne av en bruker\n" +
                ">  Hei Har du denne enn=C3=A5? 97708521 Test Testesen\n" +
                ">\n" +
                "> Henvendelsen gjelder annonsen med FINN-kode 42315910<http://www.finn.no/4=\n" +
                "2315910>\n" +
                ">\n" +
                ">\n" +
                "> Hilsen FINN.no\n" +
                ">\n" +
                ">\n" +
                '\n' +
                "--089e01183ec26859c504df595986\n" +
                "Content-Type: text/html; charset=UTF-8\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "<div><div>Det har jeg.<br></div>Test<br></div><div class=3D\"gma=\n" +
                "il_extra\"><br><br><div>14:20 17. juni 2013 skrev FINN=\n" +
                "-bruker <span>&lt;<a href=3D\"mailto:samtale-hokuspokus=\n" +
                "@innboks.finn.no\" target=3D\"_blank\">samtale-hokuspokus=\n" +
                "@innboks.finn.no</a>&gt;</span>:<br>\n" +
                "<blockquote class=3D\"gmail_quote\" style=3D\"margin:0 0 0 .8ex;border-left:1p=\n" +
                "x #ccc solid;padding-left:1ex\">  <div bgcolor=3D\"#FFFFFF\" alink=3D\"#000066\"=\n" +
                "> <table>  =\n" +
                "  <tbody>    <tr>\n" +
                "        <td style=3D\"min-height:10px\" colspan=3D\"3\" bgcolor=3D\"#A2D2EE\" hei=\n" +
                "ght=3D\"10px\"> =C2=A0</td>    </tr> <tr align=3D\"left\">        <td style=3D\"=\n" +
                "width:10px\" bgcolor=3D\"#A2D2EE\" width=3D\"10px\"> =C2=A0 </td>        <td bgc=\n" +
                "olor=3D\"#A2D2EE\" width=3D\"auto\">\n" +
                " <img alt=3D\"FINN.no\" style=3D\"display:block\" height=3D\"39\" width=3D\"120\"> =\n" +
                "</td>        <td> =\n" +
                "=C2=A0 </td>    </tr>    <tr>        <td style=3D\"min-height:10px\" colspan=\n" +
                "=3D\"3\" bgcolor=3D\"#A2D2EE\" height=3D\"10px\">\n" +
                " =C2=A0</td>    </tr>    <tr align=3D\"left\">        <td style=3D\"width:10px=\n" +
                "\" bgcolor=3D\"#FFFFFF\" width=3D\"10px\"> =C2=A0 </td>        <td bgcolor=3D\"#F=\n" +
                "FFFFF\" width=3D\"auto\">            <table> <tbody bgcolor=3D\"#FFFFFF\">      =\n" +
                "      <tr>                <td>\n" +
                " =C2=A0</td>            </tr>            <tr>                <td style=3D\"f=\n" +
                "ont-size:12px;font-family:Arial,Helvetica,sans-serif\"><h3 style>Hei!</h3><p=\n" +
                ">FINN.no har sendt deg en melding p=EF=BF=BD vegne av en bruker</p><table s=\n" +
                "tyle=3D\"border:1px dotted #999;width:450px\" cellpadding=3D\"0\" cellspacing=\n" +
                "=3D\"0\" width=3D\"450px\">\n" +
                '\n' +
                "      <tbody><tr>\n" +
                "        <td>\n" +
                "          Hei\n" +
                '\n' +
                "Har du denne enn=C3=A5?=20\n" +
                '\n' +
                "<a href=3D\"tel:97708521\" value=3D\"+4797708521\" target=3D\"_blank\">97708521</=\n" +
                "a>\n" +
                '\n' +
                "Test Testesen\n" +
                "        </td>\n" +
                "      </tr>\n" +
                "    </tbody></table><p>Henvendelsen gjelder\n" +
                "      <a href=3D\"http://www.finn.no/42315910\" target=3D\"_blank\">annonsen me=\n" +
                "d FINN-kode 42315910</a>\n" +
                "    </p><div><br><br>Hilsen FINN.=\n" +
                "no<br></div></td></tr><tr><td>=C2=\n" +
                "=A0</td></tr></tbody></table></td><td style=3D\"width:10px\" bgcolor=3D\"#FFFF=\n" +
                "FF\" width=3D\"10px\">\n" +
                "=C2=A0</td></tr><tr><td style=3D\"min-height:10px\" colspan=3D\"3\" bgcolor=3D\"=\n" +
                "#A2D2EE\" height=3D\"10px\">=C2=A0</td></tr></tbody></table></div>\n" +
                "</blockquote></div><br></div>\n" +
                '\n' +
                "--089e01183ec26859c504df595986--";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        final String filteredContents = contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain());

        assertThat(filteredContents, is("Det har jeg.\nTest Testesen"));
    }
    @Test
    public void testMessage_ShouldNotBeBlank_whenBottomPost() throws MessagingException, IOException {
        String messageSource = "Received: from mod1.finntech.no (mod1.finntech.no [152.90.242.91])\n" +
                "        by FINN.no outbound SMTP relay\n" +
                "        with SMTP (SubEthaSMTP null) id HI32CJL4\n" +
                "        for someone@hotmail.com;\n" +
                "        Tue, 18 Jun 2013 14:23:34 +0200 (CEST)\n" +
                "Message-ID: <51C05142.1000906@gmail.com>\n" +
                "Date: Tue, 18 Jun 2013 14:23:30 +0200\n" +
                "From: Test Testesen <samtale-hokuspokus@innboks.finn.no>\n" +
                "User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:17.0) Gecko/20130510 Thunderbird/17.0.6\n" +
                "MIME-Version: 1.0\n" +
                "To: someone@hotmail.com\n" +
                "Subject: =?UTF8?Q?Re:_Re:_Re:_Skoda_Yeti_Active_2.0_TD?=\n" +
                " =?UTF8?Q?i_4x4__2010,_47=C2=A0000_km,_kr_263=C2=A0111,-?=\n" +
                "References: <44529577.202058218.1371557385395.JavaMail.noreply@finn.no> <51C04F12.1@gmail.com> <51C050FC.6030206@hotmail.com>\n" +
                "In-Reply-To: <51C050FC.6030206@hotmail.com>\n" +
                "Content-Type: multipart/alternative;\n" +
                " boundary=\"------------050509000107070009030109\"\n" +
                "Content-Transfer-Encoding: 7BIT\n" +
                "Reply-To: Test Testesen <samtale-hokuspokus@innboks.finn.no>\n" +
                '\n' +
                "This is a multi-part message in MIME format.\n" +
                "--------------050509000107070009030109\n" +
                "Content-Type: text/plain; charset=utf-8\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "Den 18. juni 2013 14:22, skrev Test Testesen:\n" +
                "> Takkskruha\n" +
                "> Hotti\n" +
                "> 14:14 18. juni 2013 skrev Test Testesen:\n" +
                ">> Javipst\n" +
                ">> Test Testesen\n" +
                ">> 14:09 18. juni 2013 skrev Test:\n" +
                ">>>\n" +
                ">>>\n" +
                ">>>       Hei!\n" +
                ">>>\n" +
                ">>> FINN.no har sendt deg en melding p=C3=A5 vegne av Test Testesen\n" +
                ">>>\n" +
                ">>> Skarruselleneller?\n" +
                ">>> Hilsen\n" +
                ">>> Test Testesen\n" +
                ">>>\n" +
                ">>> Henvendelsen gjelder annonsen med FINN-kode 0=20\n" +
                ">>> <http://www.finn.no/finn/object?finnkode=3D0>\n" +
                ">>>\n" +
                ">>> *Stusser du over avsenders e-postadresse?*\n" +
                ">>>\n" +
                ">>> For =C3=A5 redusere muligheten for spam ser verken du eller mottaker=20\n" +
                ">>> hverandres e-postadresse.\n" +
                ">>> Det fungerer fortsatt =C3=A5 trykke svar/reply i e-posten. Mer om maske=\n" +
                "rt=20\n" +
                ">>> e-post <http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser>\n" +
                ">>>\n" +
                ">>\n" +
                ">\n" +
                "V=C3=A6rsjego\n" +
                '\n' +
                "--------------050509000107070009030109\n" +
                "Content-Type: text/html; charset=utf-8\n" +
                "Content-Transfer-Encoding: 7bit\n" +
                '\n' +
                "<html>\n" +
                "  <head>\n" +
                "    <meta content=\"text/html; charset=ISO-8859-1\"\n" +
                "      http-equiv=\"Content-Type\">\n" +
                "  </head>\n" +
                "  <body text=\"#000000\" bgcolor=\"#FFFFFF\">\n" +
                "    <div>Den 18. juni 2013 14:22, skrev Test\n" +
                "      Testesen:<br>\n" +
                "    </div>\n" +
                "    <blockquote cite=\"mid:(epost skjult av FINN.no)%3E\"\n" +
                "      type=\"cite\">\n" +
                "      <meta content=\"text/html; charset=ISO-8859-1\"\n" +
                "        http-equiv=\"Content-Type\">\n" +
                "      <div>Takkskruha<br>\n" +
                "        Hotti<br>\n" +
                "        14:14 18. juni 2013 skrev Test Testesen:<br>\n" +
                "      </div>\n" +
                "      <blockquote>\n" +
                "        <meta content=\"text/html; charset=ISO-8859-1\"\n" +
                "          http-equiv=\"Content-Type\">\n" +
                "        <div>Javipst<br>\n" +
                "          Test<br>\n" +
                "          14:09 18. juni 2013 skrev Test:<br>\n" +
                "        </div>\n" +
                "        <blockquote>\n" +
                "          <table>\n" +
                "            <tbody>\n" +
                "              <tr>\n" +
                "                <td>\n" +
                "                  <h3 style=\"color:#000;\">Hei!</h3>\n" +
                "                  <p> FINN.no har sendt deg en melding p&aring; vegne av Test Testesen\n" +
                "                  </p>\n" +
                "                  <table style=\"border:1px dotted #999; width:450px\"\n" +
                "                    cellpadding=\"0\" cellspacing=\"0\" width=\"450px\">\n" +
                "                    <tbody>\n" +
                "                      <tr>\n" +
                "                        <td style=\"background:#DEF; padding:8px;\"\n" +
                "                          width=\"450px\">\n" +
                "                          <div>Skarruselleneller?</div>\n" +
                "                        </td>\n" +
                "                      </tr>\n" +
                "                      <tr>\n" +
                "                        <td style=\"background:#DEF; padding:8px;\"\n" +
                "                          width=\"450px\"> Hilsen <br>\n" +
                "                          Test Testesen</td>\n" +
                "                      </tr>\n" +
                "                    </tbody>\n" +
                "                  </table>\n" +
                "                  <p> Henvendelsen gjelder <a moz-do-not-send=\"true\"\n" +
                "                      href=\"http://www.finn.no/finn/object?finnkode=0\"\n" +
                "                      target=\"_blank\">annonsen med FINN-kode 0</a>\n" +
                "                  </p>\n" +
                "                  <strong>Stusser du over avsenders e-postadresse?</strong>\n" +
                "                  <p>For &aring; redusere muligheten for spam ser verken du\n" +
                "                    eller mottaker hverandres e-postadresse. <br>\n" +
                "                    Det fungerer fortsatt &aring; trykke svar/reply i\n" +
                "                    e-posten. <a moz-do-not-send=\"true\"\n" +
                "                      href=\"http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser\">Mer\n" +
                "                      om maskert e-post</a></p>\n" +
                "                </td>\n" +
                "              </tr>\n" +
                "            </tbody>\n" +
                "          </table>\n" +
                "          <!--Checksum: d7e25dd8c87705e247a99e603e9c84d75e7093076e0e3f25a9900d2ff9b411b28eebae820503b9d6c270ce657278fc01210b6078c6c54f98cc9c253bd5409786-->\n" +
                "        </blockquote>\n" +
                "        <br>\n" +
                "      </blockquote>\n" +
                "      <br>\n" +
                "    </blockquote>\n" +
                "    V&aelig;rsjego<br>\n" +
                "  </body>\n" +
                "</html>\n" +
                '\n' +
                "--------------050509000107070009030109--\n";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        final String filteredContents = contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain());

        assertThat(filteredContents, is("Værsjego"));
    }

    @Test
    public void testMessage_shouldBeClean_FromHotmail() throws MessagingException, IOException {
        String messageSource = "                                                                                                                                                                                                                                                               \n" +
                "Return-Path: <someone@gmail.com>\n" +
                "Received: from [192.168.40.81] ([80.91.33.141])\n" +
                "        by mx.google.com with ESMTPSA id ea14sm5134521lbb.11.2013.06.18.23.35.25\n" +
                "        for <samtale-hokuspokus@innboks.finn.no>\n" +
                "        (version=TLSv1 cipher=ECDHE-RSA-RC4-SHA bits=128/128);\n" +
                "        Tue, 18 Jun 2013 23:35:26 -0700 (PDT)\n" +
                "Message-ID: <51C15128.50407@gmail.com>\n" +
                "Date: Wed, 19 Jun 2013 08:35:20 +0200\n" +
                "From: Test Testesen <someone@gmail.com>\n" +
                "User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:17.0) Gecko/20130510 Thunderbird/17.0.6\n" +
                "MIME-Version: 1.0\n" +
                "To: Test Testesen <samtale-hokuspokus@innboks.finn.no>\n" +
                "Subject: Re: Snowboards m/sko og trekk, 3 stk\n" +
                "References: <40454335.224921177.1371623225026.JavaMail.noreply@finn.no>,<CAHEF0d39QWaLCKaSH9_e4yTAfXCcnCR379HVQ9JDwXuB5UVudQ@mail.gmail.com> <DUB114-W29289A8C97C04C823A7B45AC8D0@phx.gbl>\n" +
                "In-Reply-To: <DUB114-W29289A8C97C04C823A7B45AC8D0@phx.gbl>\n" +
                "Content-Type: multipart/alternative;\n" +
                " boundary=\"------------040700040303060102060908\"\n" +
                '\n' +
                "This is a multi-part message in MIME format.\n" +
                "--------------040700040303060102060908\n" +
                "Content-Type: text/plain; charset=UTF-8; format=flowed\n" +
                "Content-Transfer-Encoding: 8bit\n" +
                '\n' +
                "Den 19. juni 2013 08:29, skrev Test Testesen:\n" +
                "> Jeg også!\n" +
                ">\n" +
                "> ------------------------------------------------------------------------\n" +
                "> Date: Wed, 19 Jun 2013 08:28:04 +0200\n" +
                "> Subject: Re: Snowboards m/sko og trekk, 3 stk\n" +
                "> From: samtale-hokuspokus@innboks.finn.no\n" +
                "> To: (epost skjult av FINN.no)\n" +
                ">\n" +
                "> Jeg også bare tester\n" +
                ">\n" +
                ">\n" +
                "> 08:27 19. juni 2013 skrev Test\n" +
                "> <samtale-hokuspokus@innboks.finn.no \n" +
                "> <mailto:samtale-hokuspokus@innboks.finn.no>>:\n" +
                ">\n" +
                ">\n" +
                ">           Hei!\n" +
                ">\n" +
                ">     FINN.no har sendt deg en melding på vegne av Test Testesen\n" +
                ">     Jeg bare tester\n" +
                ">     Hilsen\n" +
                ">     Test Testesen\n" +
                ">\n" +
                ">     Henvendelsen gjelder annonsen med FINN-kode 0\n" +
                ">     <http://www.finn.no/finn/object?finnkode=0>\n" +
                ">     *Stusser du over avsenders e-postadresse?* For å redusere\n" +
                ">     muligheten for spam ser verken du eller mottaker hverandres\n" +
                ">     e-postadresse.\n" +
                ">     Det fungerer fortsatt å trykke svar/reply i e-posten. Mer om\n" +
                ">     maskert e-post\n" +
                ">     <http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser>\n" +
                ">\n" +
                ">\n" +
                "Og jeg tester videre...\n" +
                '\n' +
                "--------------040700040303060102060908\n" +
                "Content-Type: text/html; charset=UTF-8\n" +
                "Content-Transfer-Encoding: 8bit\n" +
                '\n' +
                "<html>\n" +
                "  <head>\n" +
                "    <meta content=\"text/html; charset=UTF-8\" http-equiv=\"Content-Type\">\n" +
                "  </head>\n" +
                "  <body text=\"#000000\" bgcolor=\"#FFFFFF\">\n" +
                "    <div>Den 19. juni 2013 08:29, skrev Test\n" +
                "      Testesen:<br>\n" +
                "    </div>\n" +
                "    <blockquote cite=\"mid:DUB114-W29289A8C97C04C823A7B45AC8D0@phx.gbl\"\n" +
                "      type=\"cite\">\n" +
                "      <style><!--\n" +
                ".hmmessage P\n" +
                "{\n" +
                "margin:0px;\n" +
                "padding:0px\n" +
                "}\n" +
                "body.hmmessage\n" +
                "{\n" +
                "font-size: 12pt;\n" +
                "font-family:Calibri\n" +
                "}\n" +
                "--></style>\n" +
                "      <div>Jeg også!<br>\n" +
                "        <br>\n" +
                "        <div>\n" +
                "          <hr>Date: Wed, 19 Jun 2013 08:28:04 +0200<br>\n" +
                "          Subject: Re: Snowboards m/sko og trekk, 3 stk<br>\n" +
                "          From:\n" +
                "          <a class=\"moz-txt-link-abbreviated\" href=\"mailto:samtale-hokuspokus@innboks.finn.no\">samtale-hokuspokus@innboks.finn.no</a><br>\n" +
                "          To: (epost skjult av FINN.no)<br>\n" +
                "          <br>\n" +
                "          <div>Jeg også bare tester<br>\n" +
                "          </div>\n" +
                "          <div><br>\n" +
                "            <br>\n" +
                "            <div>08:27 19. juni 2013 skrev Test\n" +
                "              Testesen <span>&lt;<a moz-do-not-send=\"true\"\n" +
                "                  href=\"mailto:samtale-hokuspokus@innboks.finn.no\"\n" +
                "                  target=\"_blank\">samtale-hokuspokus@innboks.finn.no</a>&gt;</span>:<br>\n" +
                "              <blockquote class=\"ecxgmail_quote\" style=\"border-left:1px\n" +
                "                #ccc solid;padding-left:1ex;\">\n" +
                "                <table>\n" +
                "                  <tbody>\n" +
                "                    <tr>\n" +
                "                      <td>\n" +
                "                        <h3>Hei!</h3>\n" +
                "                        FINN.no har sendt deg en melding på vegne av\n" +
                "                        Test Hot <br>\n" +
                "                        <table style=\"border:1px dotted\n" +
                "                          #999;width:450px;\" cellpadding=\"0\"\n" +
                "                          cellspacing=\"0\" width=\"450px\">\n" +
                "                          <tbody>\n" +
                "                            <tr>\n" +
                "                              <td style=\"background:#def;padding:8px;\"\n" +
                "                                width=\"450px\">\n" +
                "                                <div>Jeg bare tester</div>\n" +
                "                              </td>\n" +
                "                            </tr>\n" +
                "                            <tr>\n" +
                "                              <td style=\"background:#def;padding:8px;\"\n" +
                "                                width=\"450px\"> Hilsen <br>\n" +
                "                                Test Hot</td>\n" +
                "                            </tr>\n" +
                "                          </tbody>\n" +
                "                        </table>\n" +
                "                        Henvendelsen gjelder <a moz-do-not-send=\"true\"\n" +
                "href=\"http://www.finn.no/finn/object?finnkode=0\" target=\"_blank\">annonsen\n" +
                "                          med FINN-kode 0</a> <br>\n" +
                "                        <strong>Stusser du over avsenders e-postadresse?</strong>\n" +
                "                        For å redusere muligheten for spam ser verken du\n" +
                "                        eller mottaker hverandres e-postadresse. <br>\n" +
                "                        Det fungerer fortsatt å trykke svar/reply i\n" +
                "                        e-posten. <a moz-do-not-send=\"true\"\n" +
                "                          href=\"http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser\"\n" +
                "                          target=\"_blank\">Mer om maskert e-post</a><br>\n" +
                "                      </td>\n" +
                "                    </tr>\n" +
                "                  </tbody>\n" +
                "                </table>\n" +
                "              </blockquote>\n" +
                "            </div>\n" +
                "            <br>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </blockquote>\n" +
                "    Og jeg tester videre...<br>\n" +
                "  </body>\n" +
                "</html>\n" +
                '\n' +
                "--------------040700040303060102060908--\n";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        final String filteredContents = contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain());

        assertThat(filteredContents, is("Og jeg tester videre..."));
    }

    @Test
    public void test_RFC_1036_Signatures() {
        final String plainText = "On Tue, 2013-06-18 at 13:09 +0000, Test Testesen wrote:\n" +
                "> Test2, Test3: Har dere noen kommentarer? ”No news is good news”\n" +
                '\n' +
                "Nothing. (But small things that can go under the radar).\n" +
                '\n' +
                "~someone\n" +
                '\n' +
                '\n' +
                "-- \n" +
                "\"This above all: to thine own self be true. It must follow that you cannot then be false to any man.\" Shakespeare \n" +
                '\n' +
                "| http://github.com/finn-no | http://tech.finn.no |\n";

        final String expected = "Nothing. (But small things that can go under the radar).\n\n" +
                "~someone";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }

    @Test
    public void testLiteralMonthNameAndSingleDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "4. juli 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testLiteralMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. juli 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testJanuaryMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. januar 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testFebruaryMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. februar 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testMarchMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. mars 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testAprilMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. april 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testMayMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. mai 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testJuneMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. juni 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testJulyMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. juli 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testAugustMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. august 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testSeptemberMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. september 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testOctoberMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. oktober 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testNovemberMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. november 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }
    @Test
    public void testDecemberMonthNameAndTwoDigitDay() {
        final String plainText = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n" +
                '\n' +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                "14. desember 2013 kl. 21:31 skrev test <samtale-hokuspokus@innboks.finn.no>:\n" +
                "|\n" +
                "|    Hei!\n" +
                "|\n" +
                "|    FINN.no har sendt deg en melding på vegne av test ";

        final String expected = "Ja, jeg skrev jo i annonsen at jeg skulle legge ut bilde snart. Skal huske å gjøre det i kveld. Det er sannsynligvis folk hjemme i helga, men det kan jeg si fra om når jeg har fått tatt bilde.\n\nTest Testesen";
        assertThat(contentFilter.filterContent("", plainText), is(expected));
    }

    @Test
    public void testWeirdCutoff() throws MessagingException, IOException {
        final String messageSource = "                                                                                                                                                                                                                                                               \n" +
                "MIME-Version: 1.0\n" +
                "Received: by 10.64.35.201 with HTTP; Sun, 22 Sep 2013 07:47:35 -0700 (PDT)\n" +
                "In-Reply-To: <44897565.251601982.1379859903196.JavaMail.noreply@finn.no>\n" +
                "References: <44897565.251601982.1379859903196.JavaMail.noreply@finn.no>\n" +
                "Date: Sun, 22 Sep 2013 16:47:35 +0200\n" +
                "Delivered-To: someone@gmail.com\n" +
                "Message-ID: <CADL-r7+z9JLh2QjQXsktUHs6Uf1WogweZ=sW1BStFZ43oZoYeQ@mail.gmail.com>\n" +
                "Subject: =?ISO-8859-1?Q?Re=3A_Vinterdress_Reima=2C_voksipose_og_diverse_h=F8st=2D_o?=\n" +
                "\t=?ISO-8859-1?Q?g_vinterutstyr?=\n" +
                "From: Test Testesen <someone@gmail.com>\n" +
                "To: FINN-bruker <samtale-hokuspokus@innboks.finn.no>\n" +
                "Content-Type: multipart/alternative; boundary=002354435a92fc17ff04e6f9fb19\n" +
                '\n' +
                "--002354435a92fc17ff04e6f9fb19\n" +
                "Content-Type: text/plain; charset=ISO-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "Hei.\n" +
                '\n' +
                "Posen er fra h=F8sten 2010. Brukt av ett barn. Posen kommer fra fabrikk med\n" +
                "\"perforering\" i ryggen for at man skal kunne ta hull for =E5 f=E5 gjennom s=\n" +
                "eler\n" +
                "i barnevogn. Denne har vi tatt hull i, ellers er posen fin og hel.\n" +
                "Forlengeren er nesten ikke brukt.\n" +
                '\n' +
                "Har du mulighet til =E5 komme og se?\n" +
                '\n' +
                "Mvh\n" +
                "Test Testesen\n" +
                '\n' +
                '\n' +
                '\n' +
                "22. september 2013 kl. 16:25 skrev FINN-bruker <\n" +
                "samtale-hokuspokus@innboks.finn.no>:\n" +
                '\n' +
                "> Hei!\n" +
                ">\n" +
                "> FINN.no har sendt deg en melding p=E5 vegne av en bruker\n" +
                ">  Hei! Jeg er interessert i voksiposen du har lagt ut. Hvor gammel er den?\n" +
                "> Hvor mange barn har brukt den? Mvh Test Testesen\n" +
                ">\n" +
                "> Henvendelsen gjelder annonsen med FINN-kode 0<http://www.finn.no/f=\n" +
                "inn/object?finnkode=3D0>\n" +
                ">\n" +
                "> *Stusser du over avsenders e-postadresse?*\n" +
                ">\n" +
                "> For =E5 redusere muligheten for spam ser verken du eller mottaker hverand=\n" +
                "res\n" +
                "> e-postadresse.\n" +
                "> Det fungerer fortsatt =E5 trykke svar/reply i e-posten. Mer om maskert\n" +
                "> e-post <http://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser>\n" +
                ">\n" +
                '\n' +
                "--002354435a92fc17ff04e6f9fb19\n" +
                "Content-Type: text/html; charset=ISO-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "<div>Hei.<div><br></div><div>Posen er fra h=F8sten 2010. Brukt =\n" +
                "av ett barn. Posen kommer fra fabrikk med &quot;perforering&quot; i ryggen =\n" +
                "for at man skal kunne ta hull for =E5 f=E5 gjennom seler i barnevogn. Denne=\n" +
                " har vi tatt hull i, ellers er posen fin og hel. Forlengeren er nesten ikke=\n" +
                " brukt.=A0</div>\n" +
                "<div><br></div><div>Har du mulighet til =E5 komme og se?</div><div><br></di=\n" +
                "v><div>Mvh</div><div>Test Testesen</div><div><br></div></div><div class=3D\"gmail=\n" +
                "_extra\"><br><br><div>22. september 2013 kl. 16:25 skr=\n" +
                "ev FINN-bruker <span>&lt;<a href=3D\"mailto:samtale-hokuspokus@innb=\n" +
                "oks.finn.no\" target=3D\"_blank\">samtale-hokuspokus@innboks.finn.no</a>&gt;</spa=\n" +
                "n>:<br>\n" +
                "<blockquote class=3D\"gmail_quote\" style=3D\"margin:0 0 0 .8ex;border-left:1p=\n" +
                "x #ccc solid;padding-left:1ex\"><table><tbody><tr><td>\n" +
                "    <h3 style>Hei!</h3>\n" +
                "    <p>\n" +
                "    FINN.no har sendt deg en melding p=E5 vegne av\n" +
                "                    en bruker\n" +
                "                </p>\n" +
                "    <table style=3D\"border:1px dotted #999;width:450px\" cellpadding=3D\"0\" c=\n" +
                "ellspacing=3D\"0\" width=3D\"450px\">\n" +
                "        <tbody><tr><td style=3D\"background:#def;padding:8px\" width=3D\"450px=\n" +
                "\">\n" +
                "        <div>Hei! Jeg er interessert i voksiposen du har lagt ut. Hvor gamm=\n" +
                "el er den? Hvor mange barn har brukt den? Mvh Test Testesen </div>\n" +
                "    </td></tr>\n" +
                "            </tbody></table>\n" +
                "    <p>\n" +
                "            Henvendelsen gjelder  <a href=3D\"http://www.finn.no/finn/object=\n" +
                "?finnkode=0\" target=3D\"_blank\">annonsen med FINN-kode 0</a>=\n" +
                "=20\n" +
                "                                    </p>\n" +
                "    <p>=\n" +
                "</p>\n" +
                "    <strong>Stusser du over avsenders e-postadresse?</strong>\n" +
                "    <p>For =E5 redusere muligheten for spam ser verken du eller mottaker hv=\n" +
                "erandres e-postadresse. <br>\n" +
                "    Det fungerer fortsatt =E5 trykke svar/reply i e-posten. <a href=3D\"http=\n" +
                "://ks.finn.no/trygg-pa-finn/maskering-av-epostadresser\" target=3D\"_blank\">M=\n" +
                "er om maskert e-post</a></p>\n" +
                "</td></tr></tbody></table>\n" +
                "</blockquote></div><br></div>\n" +
                '\n' +
                "--002354435a92fc17ff04e6f9fb19--";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        final String filteredContents = contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain());

        String expected = "Hei.\n\n" +
                "Posen er fra høsten 2010. Brukt av ett barn. Posen kommer fra fabrikk med\n" +
                "\"perforering\" i ryggen for at man skal kunne ta hull for å få gjennom seler\n" +
                "i barnevogn. Denne har vi tatt hull i, ellers er posen fin og hel.\n" +
                "Forlengeren er nesten ikke brukt.\n\n" +
                "Har du mulighet til å komme og se?\n\n" +
                "Mvh\n" +
                "Test Testesen";
        assertThat(filteredContents, is(expected));
    }

    @Test
    public void testMessage_shouldBeClean_FromGmail() throws MessagingException, IOException {
        String messageSource = "MIME-Version: 1.0\n" +
                "Received: by 10.70.94.233 with HTTP; Tue, 4 Feb 2014 03:36:03 -0800 (PST)\n" +
                "In-Reply-To: <51592658.291540815.1391513610801.JavaMail.noreply@finn.no>\n" +
                "References: <51592522.291540406.1391513368290.JavaMail.noreply@finn.no>\n" +
                "\t<51592658.291540815.1391513610801.JavaMail.noreply@finn.no>\n" +
                "Date: Tue, 4 Feb 2014 12:36:03 +0100\n" +
                "Delivered-To: someone@gmail.com\n" +
                "Message-ID: <CAA96wTTbVLzCKSOpdM_LHdjmzAkBWftxhuqRtiZG=4B=y8dNFQ@mail.gmail.com>\n" +
                "Subject: =?ISO-8859-1?B?UmU6IFP4dm4=?=\n" +
                "From: Test Testesen <someone@gmail.com>\n" +
                "To: Test Testesen <samtale-hokuspokus@innboks.finn.no>\n" +
                "Content-Type: multipart/alternative; boundary=001a11c2da1097a76204f1930bb3\n" +
                '\n' +
                "--001a11c2da1097a76204f1930bb3\n" +
                "Content-Type: text/plain; charset=ISO-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "Den er god. Da m=E5 vi snakkes mer.\n" +
                '\n' +
                '\n' +
                "2014-02-04 Test Testesen <samtale-hokuspokus@innboks.finn.no>:\n" +
                '\n' +
                "> Den koster rundt 100kr.\n" +
                ">\n" +
                '\n' +
                "--001a11c2da1097a76204f1930bb3\n" +
                "Content-Type: text/html; charset=ISO-8859-1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                '\n' +
                "<div>Den er god. Da m=E5 vi snakkes mer.</div><div class=3D\"gma=\n" +
                "il_extra\"><br><br><div>2014-02-04 Test Testesen <span d=\n" +
                "ir=3D\"ltr\">&lt;<a href=3D\"mailto:samtale-hokuspokus@innboks.finn.no\" target=\n" +
                "=3D\"_blank\">samtale-hokuspokus@innboks.finn.no</a>&gt;</span>:<br>\n" +
                "<blockquote class=3D\"gmail_quote\" style=3D\"margin:0 0 0 .8ex;border-left:1p=\n" +
                "x #ccc solid;padding-left:1ex\"><div>Den koster rundt 100kr.</div>\n" +
                "</blockquote></div><br></div>\n" +
                '\n' +
                "--001a11c2da1097a76204f1930bb3--";
        SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        final String filteredContents = contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain());

        assertThat(filteredContents, is("Den er god. Da må vi snakkes mer."));
    }

    @Test
    public void shouldExtractHtmlContentFromRingdalNo() throws MessagingException, IOException {
        final String messageSource= "MIME-Version: 1.0\n" +
                "From: \"Test Testesen=via FINN.no\" <samtale-hokuspokus@innboks.finn.no>\n" +
                "To: Test Testesen <someone@somewhere.no>\n" +
                "Date: Fri, 21 Mar 2014 13:29:35 +0100\n" +
                "Subject: =?UTF8?Q?RE:_Lager_til_leie_i_Haugesund_Kvala_minilage?=\n" +
                " =?UTF8?Q?r/container_utleie_kj=C3=B8per_og_selger_containere?=\n" +
                "Content-Type: text/html; charset=utf-8\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                "Message-ID: <c57ad5c2-4d5b-4fce-8dfc-2fd061f9993f@OSYS-EXCHANGE2.omega.no>\n" +
                "Reply-To: \n" +
                "\t\"Test Testesen via FINN.no\" <samtale-hokuspokus@innboks.finn.no>\n" +
                "\n" +
                "<html>\n" +
                "    <head>\n" +
                "    </head>\n" +
                "    <body alink=3D\"#000066\" style=3D\"font-family: verdana; font-size: 8pt; =\n" +
                "background-color: #ffffff;\">\n" +
                "        <div style=3D\"font-style: normal; font-variant: normal; font-weight=\n" +
                ": normal; font-size: 8pt; line-height: normal; font-family: verdana;\">Hei! =\n" +
                "Vi har fortsatt ledig lagerplass i 20 og 40 fots containere. Ring 99229798 =\n" +
                "for mer info. &nbsp;Hilsen Test Testesen<br />\n" +
                "        <br />\n" +
                "        </div>\n" +
                "        <div style=3D\"font-style: normal; font-variant: normal; font-weight=\n" +
                ": normal; font-size: 8pt; line-height: normal; font-family: verdana;\"></div=\n" +
                ">\n" +
                "        <table style=3D\"font-style: normal; font-variant: normal; font-weig=\n" +
                "ht: normal; font-size: 8pt; line-height: normal; font-family: verdana, aria=\n" +
                "l;\" cellspacing=3D\"0\" cellpadding=3D\"0\" width=3D\"100%\" border=3D\"0\">\n" +
                "            <tbody>\n" +
                "                <tr valign=3D\"top\" bgcolor=3D\"#c0c0c0\" height=3D\"15\">\n" +
                "                    <td><strong>From:</strong></td>\n" +
                "                    <td>\"Andreas via FINN.no\" &lt;samtale-hokuspokus@innboks=\n" +
                ".finn.no&gt;   (19. mar. 2014 14:48)</td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td><strong>To:</=\n" +
                "strong></td>\n" +
                "                    <td>\"(epost skjult av FINN.no)\"=\n" +
                " &lt;(epost skjult av FINN.no)&gt;</td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td><strong>Subject:</strong></td>\n" +
                "                    <td>Lager til leie i Haugesund Kvala minilager/containe=\n" +
                "r utleie kj&oslash;per og selger containere</td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td style=3D\"font-size: 2pt; height: 2px; background-co=\n" +
                "lor: #c0c0c0;\" colspan=3D\"2\"></td>\n" +
                "                </tr>\n" +
                "            </tbody>\n" +
                "        </table>\n" +
                "        <br />\n" +
                "        <br />\n" +
                "        <style type=3D\"text/css\">\n" +
                "            body {        font-family:Arial, Sans-serif;        font-size:1=\n" +
                "2px;        margin:15px;        }    td, th, p {        font-family: Arial,=\n" +
                " Sans-serif;        font-size:12px;        }    a:link {        color: #000=\n" +
                "066;        }    h1,h2,h3,h4,h5,h6,.h1-inline,.h2-inline,.h3-inline,.h4-inl=\n" +
                "ine,.h5-inline,.h6-inline {        padding:0;        font-weight:bold;    }=\n" +
                "    h1,.h1-inline { font-size:1.8em; }    h2,.h2-inline { font-size:1.6em; =\n" +
                "}    h3,.h3-inline { font-size:1.4em; }    h4,.h4-inline { font-size:1.3em;=\n" +
                " }    h5,.h5-inline { font-size:1.2em; }    h6,.h6-inline { font-size:1.1em=\n" +
                "; }    td.title {        font-size: 24px;        font-weight: bold;        =\n" +
                "color: #000066;        vertical-align: bottom;        padding:8px;        }=\n" +
                "    div.regards{        font-size: 16px;        font-weight: bold;        c=\n" +
                "olor: #000066;        }    span.bluetext{        font-weight: bold;        =\n" +
                "color: #000066;    }    div.regardsCv{        font-size: 12px;        font-=\n" +
                "weight: normal;        color: #000066;        }    #addonproduct {        w=\n" +
                "idth:100%;    }    #addonproduct .left {        width:300px;        height:=\n" +
                "100px;        float:left;        background-color:#eee;        padding:5px;=\n" +
                "        margin:5px;    }    #addonproduct .left img {        float:left;   =\n" +
                "     margin-right:5px;    }    #addonproduct .right {        width:300px;  =\n" +
                "      height:100px;        float:left;        background-color:#eee;       =\n" +
                " padding:5px;        margin:5px;    }    #addonproduct .right img {        =\n" +
                "float:right;        margin-right:5px;    }    #addonproduct .clearfix {    =\n" +
                "    clear:both;        height:0.1px;        line-height:0.1px;        font-=\n" +
                "size:0.1px;    }    .red {        color:#ff0000;    }\n" +
                "        </style>\n" +
                "        <table cellpadding=3D\"0\" cellspacing=3D\"0\" border=3D\"0\" width=3D\"10=\n" +
                "0%\">\n" +
                "            <tbody>\n" +
                "                <tr>\n" +
                "                    <td style=3D\"height: 10px; background-color: #a2d2ee;\" =\n" +
                "colspan=3D\"3\"> &nbsp;</td>\n" +
                "                </tr>\n" +
                "                <tr align=3D\"left\">\n" +
                "                    <td> =\n" +
                "&nbsp; </td>\n" +
                "                    <td> <img src=3D\"h=\n" +
                "ttp://cache.finn.no/img/mail/logo.gif\" alt=3D\"FINN.no\" width=3D\"120\" height=\n" +
                "=3D\"39\" style=3D\"display: block;\" /> </td>\n" +
                "                    <td> =\n" +
                "&nbsp; </td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td style=3D\"height: 10px; background-color: #a2d2ee;\" =\n" +
                "colspan=3D\"3\"> &nbsp;</td>\n" +
                "                </tr>\n" +
                "                <tr align=3D\"left\">\n" +
                "                    <td> =\n" +
                "&nbsp; </td>\n" +
                "                    <td>\n" +
                "                    <table>\n" +
                "                        <tbody bgcolor=3D\"#FFFFFF\">\n" +
                "                            <tr>\n" +
                "                                <td> &nbsp;</td>\n" +
                "                            </tr>\n" +
                "                            <tr>\n" +
                "                                <td style=3D\"font-size: 12px; font-family: =\n" +
                "arial, helvetica, sans-serif;\">\n" +
                "                                <h3 style=3D\"color: #000000;\">Hei!</h3>\n" +
                "                                <p>Test Testesen har sendt deg en melding via FIN=\n" +
                "N.no</p>\n" +
                "                                <table style=3D\"border: 1px dotted #999999;=\n" +
                " width: 450px;\" cellpadding=3D\"0\" cellspacing=3D\"0\" width=3D\"450px\">\n" +
                "                                    <tbody>\n" +
                "                                        <tr>\n" +
                "                                            <td style=3D\"background-color: =\n" +
                "#ddeeff; padding: 8px; width: 450px; background-position: initial initial; =\n" +
                "background-repeat: initial initial;\">\n" +
                "                                            <div>\n" +
                "                                            Hei, jeg fant en gammel annonse=\n" +
                " p&aring; finn der du leier ut minilager. Er det fortsatt tilfellet?\n" +
                "                                            Test Testesen\n" +
                "                                            </div>\n" +
                "                                            </td>\n" +
                "                                        </tr>\n" +
                "                                    </tbody>\n" +
                "                                </table>\n" +
                "                                <p>Henvendelsen gjelder:\n" +
                "                                <a href=3D\"http://www.finn.no/40066263\" tar=\n" +
                "get=3D\"_blank\">Lager til leie i Haugesund Kvala minilager/container utleie =\n" +
                "kj&oslash;per og selger containere</a>\n" +
                "                                <br />\n" +
                "                                <span>FINN-kode: =\n" +
                "40066263</span>\n" +
                "                                </p>\n" +
                "                                <p>Hele denne samtalen finner du ogs&aring;=\n" +
                " i Mine meldinger p&aring; FINN.no.<br />\n" +
                "                                <a href=3D\"https://hjelpesenter.finn.no/hc/=\n" +
                "no/sections/200043286-Mine-meldinger\">Sp&oslash;rsm&aring;l og Svar om Mine=\n" +
                " meldinger</a></p>\n" +
                "                                <p>FINN.no anonymiserer e-postadresser for =\n" +
                "&aring; redusere muligheten for spam.<br />\n" +
                "                                <a href=3D\"https://hjelpesenter.finn.no/hc/=\n" +
                "no/sections/200037958-Maskerte-e-postadresser\">Mer om maskert e-post</a></p=\n" +
                ">\n" +
                "                                <div class=3D\"regards\" style=3D\"font-size: =\n" +
                "14px; font-weight: bold; color: #000000;\"><br />\n" +
                "                                <br />\n" +
                "                                Hilsen FINN.no<br />\n" +
                "                                </div>\n" +
                "                                </td>\n" +
                "                            </tr>\n" +
                "                            <tr>\n" +
                "                                <td>&nbsp;</td>\n" +
                "                            </tr>\n" +
                "                        </tbody>\n" +
                "                    </table>\n" +
                "                    </td>\n" +
                "                    <td>&=\n" +
                "nbsp;</td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td style=3D\"height: 10px; background-color: #a2d2ee;\" =\n" +
                "colspan=3D\"3\">&nbsp;</td>\n" +
                "                </tr>\n" +
                "            </tbody>\n" +
                "        </table>\n" +
                "        <!--Checksum: ab5c41de63657670406e5530c4f388cdce92bd960a5793be210b6=\n" +
                "078c6c54f98cc9c253bd5409786-->\n" +
                "    </body>\n" +
                "</html>";

        final SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        final Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        final String filteredContents = contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain());
        // Crappy html format, no safe way to remove parent messages, just make sure reply is included
        assertEquals(filteredContents, "Hei! Vi har fortsatt ledig lagerplass i 20 og 40 fots containere. Ring 99229798 for mer info.  Hilsen Test Testesen");
    }

    @Test
    public void shouldExtractTextContentFromGmailForwardedFromHotmail() throws MessagingException, IOException {
        final String messageSource= "Delivered-To: someone@gmail.com\n" +
                "Received: by 10.52.184.167 with SMTP id ev7csp18540vdc;\n" +
                "        Thu, 5 Jun 2014 06:36:19 -0700 (PDT)\n" +
                "X-Received: by 10.152.10.40 with SMTP id f8mr1908433lab.75.1401975378718;\n" +
                "        Thu, 05 Jun 2014 06:36:18 -0700 (PDT)\n" +
                "Return-Path: <samtale-hokuspokus@innboks.finn.no>\n" +
                "Received: from postmann.schibsted-it.no (postmann.schibsted-it.no. [80.91.34.179])\n" +
                "        by mx.google.com with ESMTP id ap2si7122924lac.23.2014.06.05.06.36.18\n" +
                "        for <someone@gmail.com>;\n" +
                "        Thu, 05 Jun 2014 06:36:18 -0700 (PDT)\n" +
                "Received-SPF: pass (google.com: domain of samtale-hokuspokus@innboks.finn.no designates 80.91.34.179 as permitted sender) client-ip=80.91.34.179;\n" +
                "Authentication-Results: mx.google.com;\n" +
                "       spf=pass (google.com: domain of samtale-hokuspokus@innboks.finn.no designates 80.91.34.179 as permitted sender) smtp.mail=samtale-hokuspokus@innboks.finn.no;\n" +
                "       dkim=fail header.i=@innboks.finn.no;\n" +
                "       dmarc=pass (p=NONE dis=NONE) header.from=finn.no\n" +
                "Received: from mod02.finn.no (mod02.finn.no [152.90.242.72])\n" +
                "\tby postmann.schibsted-it.no (Postfix) with ESMTP id E6EE41023E3\n" +
                "\tfor <someone@gmail.com>; Thu,  5 Jun 2014 15:36:17 +0200 (CEST)\n" +
                "DKIM-Signature: v=1; a=rsa-sha1; c=simple/simple; d=innboks.finn.no;\n" +
                "\ts=default; t=1401975377; bh=ChwfJjv69U5DhIGZPzgV5WiNdn8=;\n" +
                "\th=MIME-Version:In-Reply-To:References:Date:Message-ID:Subject:From:\n" +
                "\t To:Content-Type:Content-Transfer-Encoding:Reply-To;\n" +
                "\tb=pAfHDw8Wl2+t/XTikdqT08vYnItq2OGg5wadenhO40f348v9D3Bd697LwjqZPpmUb\n" +
                "\t m64xzSJe9+HuOYqzfHshRxgmlawFSdVV+vQBP+RFvQM78jAwigzdSSHc3a5RWZBcx3\n" +
                "\t 8SLBT1EarsjENUbhcQ7HibEfVXiTwNp+76ikKdaY=\n" +
                "Received: from mod01.finn.no (mod01.finn.no [152.90.242.71])\n" +
                "        by FINN.no outbound SMTP relay\n" +
                "        with SMTP (SubEthaSMTP null) id HW23XWXX\n" +
                "        for someone@gmail.com;\n" +
                "        Thu, 05 Jun 2014 15:36:17 +0200 (CEST)\n" +
                "X-Spam-Status: No\n" +
                "X-Greylist: greylisting inactive for samtale-hokuspokus@innboks.finn.no in SQLgrey-1.8.0-rc2\n" +
                "MIME-Version: 1.0\n" +
                "X-Received: by 10.60.103.134 with SMTP id fw6mr67266625oeb.36.1401975225673;\n" +
                " Thu, 05 Jun 2014 06:33:45 -0700 (PDT)\n" +
                "In-Reply-To: <CAN5Jt4kmzsdRSPs-kQB59eThRw6uQdpA3Vs_vB8T7jUQGkFS5g@mail.gmail.com>\n" +
                "References: <57258469.345355236.1401970311277.JavaMail.noreply@finn.no>\n" +
                "\t<CAOmO615gH2XjUQRpyd61+TmR5HX5SgVEqzsr6_YhnZE_arC8Bg@mail.gmail.com>\n" +
                "\t<CAN5Jt4kmzsdRSPs-kQB59eThRw6uQdpA3Vs_vB8T7jUQGkFS5g@mail.gmail.com>\n" +
                "Date: Thu, 5 Jun 2014 15:33:45 +0200\n" +
                "Message-ID: <CAOmO616NvTmbp-J=jrhHrQzk4CE-CVgZ_j_khyn9KU9-sXGczg@mail.gmail.com>\n" +
                "Subject: Re: Pent bord fra ikea\n" +
                "From: Test Testesen2 <samtale-hokuspokus@innboks.finn.no>\n" +
                "To: Test Testesen <someone@gmail.com>\n" +
                "Content-Type: multipart/alternative; boundary=089e01183d644f6db004fb16cbec\n" +
                "Content-Transfer-Encoding: 7BIT\n" +
                "Reply-To: \n" +
                "\tTest Testesen2 <samtale-hokuspokus@innboks.finn.no>\n" +
                "\n" +
                "--089e01183d644f6db004fb16cbec\n" +
                "Content-Type: text/plain; charset=utf-8\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                "\n" +
                "Den er godt. Har flyttet i n=C3=A6rheten, s=C3=A5 bare gi meg noen minutter=\n" +
                " f=C3=B8r du\n" +
                "kommer. Send sms p=C3=A5 99999999 ca 10 min f=C3=B8r, s=C3=A5 ordner vi det=\n" +
                ".\n" +
                "\n" +
                "Mvh Test Testesen2\n" +
                "\n" +
                "Den torsdag 5. juni 2014 skrev Test Testesen via FINN.no <\n" +
                "samtale-hokuspokus@innboks.finn.no> f=C3=B8lgende:\n" +
                "\n" +
                "> Hvis du er hjemme etter kl 18 og utover, s=C3=A5 kan jeg kanskje bare kom=\n" +
                "me n=C3=A5r\n" +
                "> jeg f=C3=A5r tid?\n" +
                ">\n" +
                "> Har du et telefonnummer?\n" +
                ">\n" +
                "> -Test Testesen.\n" +
                ">\n" +
                ">\n" +
                "> 2014-06-05 14:25 GMT+02:00 Test Testesen2 <\n" +
                "> samtale-hokuspokus@innboks.finn.no\n" +
                "> <javascript:_e(%7B%7D,'cvml','samtale-hokuspokus@innboks.finn.no');>>:\n" +
                ">\n" +
                ">> Hei, det passer n=C3=A5r som helst egentlig. N=C3=A5r er best for deg? M=\n" +
                "vh Test Testesen2\n" +
                ">>\n" +
                ">> Den torsdag 5. juni 2014 skrev Test Testesen via FINN.no <\n" +
                ">> samtale-hokuspokus@innboks.finn.no\n" +
                ">> <javascript:_e(%7B%7D,'cvml','samtale-hokuspokus@innboks.finn.no');>>\n" +
                ">> f=C3=B8lgende:\n" +
                ">>\n" +
                ">>> Hei!\n" +
                ">>>\n" +
                ">>> Test Testesen har sendt deg en melding via FINN.no\n" +
                ">>>  Hei! Tror dette bordet kan v=C3=A6re interessant for oss. Kan vi komme=\n" +
                " =C3=A5 ta\n" +
                ">>> en titt idag? N=C3=A5r passer det is=C3=A5fall? Mvh Test Testesen =\n" +
                "/ 46 93 73 16\n" +
                ">>>   Hilsen\n" +
                ">>> Test Testesen\n" +
                ">>>\n" +
                ">>> Henvendelsen gjelder: Pent bord fra ikea\n" +
                ">>> <http://www.finn.no/finn/object?finnkode=3D0>\n" +
                ">>> FINN-kode: 0  og referanse 0.0.\n" +
                ">>>\n" +
                ">>> Hele denne samtalen finner du ogs=C3=A5 i Mine meldinger p=C3=A5 FINN.n=\n" +
                "o.\n" +
                ">>> Sp=C3=B8rsm=C3=A5l og Svar om Mine meldinger\n" +
                ">>> <https://hjelpesenter.finn.no/hc/no/sections/200043286-Mine-meldinger>\n" +
                ">>>\n" +
                ">>> FINN.no anonymiserer e-postadresser for =C3=A5 redusere muligheten for =\n" +
                "spam.\n" +
                ">>> Mer om maskert e-post\n" +
                ">>> <https://hjelpesenter.finn.no/hc/no/sections/200037958-Maskerte-e-posta=\n" +
                "dresser>\n" +
                ">>>\n" +
                ">>\n" +
                ">\n" +
                "\n" +
                "--089e01183d644f6db004fb16cbec\n" +
                "Content-Type: text/html; charset=utf-8\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                "\n" +
                "Den er godt. Har flyttet i n=C3=A6rheten, s=C3=A5 bare gi meg noen minutter=\n" +
                " f=C3=B8r du kommer. Send sms p=C3=A5 99999999 ca 10 min f=C3=B8r, s=C3=A5 =\n" +
                "ordner vi det.=C2=A0<div><br></div><div>Mvh Test Testesen2=C2=A0<br><br>Den torsd=\n" +
                "ag 5. juni 2014 skrev Test Testesen via FINN.no &lt;<a href=3D\"mai=\n" +
                "lto:samtale-hokuspokus@innboks.finn.no\">samtale-hokuspokus@innboks.finn.no</a=\n" +
                ">&gt; f=C3=B8lgende:<br>\n" +
                "<blockquote><div>Hvis du er hjemme etter kl =\n" +
                "18 og utover, s=C3=A5 kan jeg kanskje bare komme n=C3=A5r jeg f=C3=A5r tid?=\n" +
                "<div><br></div><div>\n" +
                "Har du et telefonnummer?</div><div><br></div><div>-Test Testesen.=C2=A0</div></div=\n" +
                "><div><br>\n" +
                "<br><div>2014-06-05 14:25 GMT+02:00 Test Testesen2\n" +
                "via FINN.no <span>&lt;<a href=3D\"javascript:_e(%7B%7D,&#39=\n" +
                ";cvml&#39;,&#39;samtale-hokuspokus@innboks.finn.no&#39;);\" target=3D\"_blank\"=\n" +
                ">samtale-hokuspokus@innboks.finn.no</a>&gt;</span>:<br>\n" +
                "\n" +
                "<blockquote>Hei, det passer n=C3=A5r som helst egentlig.=\n" +
                " N=C3=A5r er best for deg? Mvh Test Testesen2<br><br>Den torsdag 5. juni 2014 skr=\n" +
                "ev Test Testesen via FINN.no &lt;<a href=3D\"javascript:_e(%7B%7D,&#39;cvml&#3=\n" +
                "9;,&#39;samtale-hokuspokus@innboks.finn.no&#39;);\" target=3D\"_blank\">samtale=\n" +
                "-hokuspokus@innboks.finn.no</a>&gt; f=C3=B8lgende:<br>\n" +
                "\n" +
                "\n" +
                "<blockquote><table><tbody><tr><td>\n" +
                "    <h3 style=3D\"color:#000\">Hei!</h3>\n" +
                "    <p>\n" +
                "   =20\n" +
                "                    Test Testesen\n" +
                "                        har sendt deg en melding via FINN.no\n" +
                "    </p>\n" +
                "    <table style=3D\"border:1px dotted #999;width:450px\" cellpadding=3D\"0\" c=\n" +
                "ellspacing=3D\"0\" width=3D\"450px\">\n" +
                "        <tbody><tr><td style=3D\"background:#def;padding:8px\" width=3D\"450px=\n" +
                "\">\n" +
                "        <div>Hei! Tror dette bordet kan v=C3=A6re interessant for oss. Kan =\n" +
                "vi komme =C3=A5 ta en titt idag? N=C3=A5r passer det is=C3=A5fall?\n" +
                "\n" +
                "Mvh Test Testesen / 00000000</div>\n" +
                "    </td></tr>\n" +
                "                    <tr><td style=3D\"background:#def;padding:8px\" width=3D\"=\n" +
                "450px\">\n" +
                "                <div>\n" +
                "                Hilsen <br>Test Testesen\n" +
                "                                    </div>\n" +
                "            </td></tr>\n" +
                "            </tbody></table>\n" +
                "    <p>\n" +
                "            Henvendelsen gjelder: <a href=3D\"http://www.finn.no/finn/object=\n" +
                "?finnkode=3D0\" target=3D\"_blank\">Pent bord fra ikea</a>=20\n" +
                "        <br>\n" +
                "        <span>FINN-kode: 0\n" +
                "                    =C2=A0og referanse 0.0.\n" +
                "                </span>\n" +
                "        </p>\n" +
                "    <p>=\n" +
                "</p>\n" +
                "    <p>Hele denne samtalen finner du ogs=C3=A5 i Mine meldinger p=C3=A5 FIN=\n" +
                "N.no.<br>\n" +
                "        <a href=3D\"https://hjelpesenter.finn.no/hc/no/sections/200043286-Mi=\n" +
                "ne-meldinger\" target=3D\"_blank\">Sp=C3=B8rsm=C3=A5l og Svar om Mine meldinge=\n" +
                "r</a></p>\n" +
                "    <p>FINN.no anonymiserer e-postadresser for =C3=A5 redusere muligheten f=\n" +
                "or spam.<br>\n" +
                "        <a href=3D\"https://hjelpesenter.finn.no/hc/no/sections/200037958-Ma=\n" +
                "skerte-e-postadresser\" target=3D\"_blank\">Mer om maskert e-post</a></p>\n" +
                "</td></tr></tbody></table>\n" +
                "</blockquote>\n" +
                "</blockquote></div><br></div>\n" +
                "</blockquote></div>\n" +
                "\n" +
                "--089e01183d644f6db004fb16cbec--";

        final SMTPMessage message = new SMTPMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(messageSource.getBytes()));
        final Content content = new Content();
        List<DataSource> attachments = new MessageConverter().extractContents(message, content);
        final String filteredContents = contentFilter.filterContent(content.getBodyHtml(), content.getBodyPlain());
        assertThat(filteredContents, is("Den er godt. Har flyttet i nærheten, så bare gi meg noen minutter før du\n" +
                "kommer. Send sms på 99999999 ca 10 min før, så ordner vi det.\n\n" +
                "Mvh Test Testesen2"));
    }

    @Test
    public void shouldExtractCorrectlyFromNorwegianYahoo() {
        final String plain = "Svar\n\n" +
                "     Den Tirsdag, 17. mars 2015 15.40 skrev Lotte via FINN.no <samtale-o4xgk4jml@innboks.finn.no>:\n\n\n" +
                "  Message";

        final String extracted = contentFilter.filterContent("", plain);
        assertThat(extracted, is("Svar"));
    }

    @Test
    public void shouldExtractCorrectlyForRealExample_TKF_1331() {
        final String plainText = "En mann i di stilling må no vel klare meir enn det?? Men siden det e du så\n" +
                "skal det gå greit;-)\n" +
                "\n" +
                "Mvh\n" +
                "Alice\n" +
                "23. mars 2015 14:47 skrev \"Bob Bob via FINN.no\" <" +
                "samtale-12345678@innboks.finn.no>:\n" +
                "\n" +
                ">         [image: FINN.no]                Hei !\n" +
                "> Hilsen FINN.no\n" +
                ">\n";

        final String filteredContent = contentFilter.filterContent("", plainText);
        assertThat(filteredContent, startsWith("En mann i di stilling må no vel klare meir enn det?? Men siden det e du så\n" +
                "skal det gå greit;-)"));
    }

    @Test
    public void shouldUnderstandBankAccountNumber() {
        String plainText =
                "Hmmm, det var rart, ligger riktig i sent-boksen min?!\n" +
                        "Mulig det er noe hos Finn da..\n" +
                        '\n' +
                        "Sett inn kr 170,- på konto 9999.99.99999, og mail meg igjen når det er gjort. Jeg postlegger straks innbetalingen er på konto.\n" +
                        '\n' +
                        "Mvh Bob\n" +
                        '\n' +
                        "------------------------------\n" +
                        "On Thu, Apr 16, 2015 10:45 PM PDT FINN-bruker wrote:\n" +
                        '\n' +
                        ">Hei :)\n" +
                        ">Tror det ble litt borte av teksten din...? Kontonr, eller vil du jeg skal bruke sikker betaling på Finn?\n" +
                        ">Mvh\n" +
                        ">Bib Bib\n" +
                        ">Veiveien 999\n" +
                        ">9999 Poststed\n" +
                        '\n' +
                        "Fra: FINN.no Kundeservice <kundeservice@finn.no>\n" +
                        "Til: Bob <bob.bob@yahoo.no>\n" +
                        "Sendt: Onsdag, 22. april 2015 9.19\n" +
                        "Emne: [FINN.no Kundeservice] ang: Kutt av mailtekst til mottaker..\n";
        String content = contentFilter.filterContent("", plainText).trim();
        assertThat(content, is("Hmmm, det var rart, ligger riktig i sent-boksen min?!\n" +
                "Mulig det er noe hos Finn da..\n\n" +
                "Sett inn kr 170,- på konto 9999.99.99999, og mail meg igjen når det er gjort. Jeg postlegger straks innbetalingen er på konto.\n\n" +
                "Mvh Bob"));
    }

    @Test
    public void shouldUnderstandText_On_plusNumeral() {
        String plainText =
                "Neida, det passer utmerket!\n" +
                        "Adressen er forøvrig\n" +
                        "Konnerudgata 99\n" + // contains "on" followed by stuff followed by numeral! Used to fail when "On Thu, " etc is present.
                        "9999 Sted\n" +
                        "(lilla 11-mannsbolig med en gul motorsykkel i innkjørselen)\n" +
                        "Ring meg når du er utenfor på tlf 999 99 999 så kommer jeg ut!\n\n" +
                        "On Thu, Apr 16, 2015 10:45 PM PDT FINN-bruker wrote:\n" +
                        '\n' +
                        "Noe tekst fra svaret\n";
        String content = contentFilter.filterContent("", plainText).trim();
        assertThat(content, is("Neida, det passer utmerket!\n" +
                "Adressen er forøvrig\n" +
                "Konnerudgata 99\n" +
                "9999 Sted\n" +
                "(lilla 11-mannsbolig med en gul motorsykkel i innkjørselen)\n" +
                "Ring meg når du er utenfor på tlf 999 99 999 så kommer jeg ut!"));
    }

    private static final class WrappingContentFilter implements ContentFilter {
        public boolean canHandle(boolean isReply, String html, String plainText) {
            return true;
        }

        public String filterContent(String html, String plainText) {
            String returnValue;
            if (null != plainText && !plainText.isEmpty()) {
                returnValue = EmailReplyParser.parseReply(plainText);
            } else {
                returnValue = EmailReplyParser.parseReply(removeMarkup(html)).replace('\u00a0', ' '); // Non-breaking sp
            }
            return returnValue.trim();
        }

        public String filterSubject(String subject) {
            if (null == subject) {
                return "";
            } else {
                String regex = "([\\[\\(] *)?(RE|FWD?|Re|Fwd?|Sv|SV|Vs|VS) *([-:;)\\]][ :;\\])-]*|$)|\\]+ *$";
                return subject.replaceAll(regex, "");
            }
        }
        String removeMarkup(String html) {
            final Document document = Jsoup.parse(html);
            final TextExtractorVisitor textExtractorVisitor = new TextExtractorVisitor();
            final NodeTraversor traversor = new NodeTraversor(textExtractorVisitor);
            traversor.traverse(document.body());
            return textExtractorVisitor.toString();
        }

        static class TextExtractorVisitor implements NodeVisitor {
            private final StringBuilder sb = new StringBuilder();

            public void head(Node node, int i) {
                if (node instanceof TextNode) {
                    sb.append(((TextNode) node).getWholeText());
                }
            }

            public void tail(Node node, int i) {
                String name = node.nodeName();
                if (name.equals("br")) {
                    sb.append('\n');
                }
                else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5", "h6")) {
                    sb.append("\n\n");
                }
            }

            public String toString() {
                return sb.toString();
            }
        }
    }

}
