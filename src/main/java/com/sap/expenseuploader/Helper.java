package com.sap.expenseuploader;

import com.sap.expenseuploader.model.Expense;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.relation.RoleNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a library of helper functions which can be used by other classes
 */
public class Helper
{
    private static final Logger logger = LogManager.getLogger(Helper.class);

    public static String csrfToken = null;

    // Deltamerge of expenses
    // Returns all sourceExpenses, that do not exist already in targetExpenses
    public static List<Expense> getExpensesToAdd( List<Expense> sourceExpenses, List<Expense> targetExpenses )
    {
        List<Expense> result = new ArrayList<>();
        int i = 0;
        int j = 0;

        // Both lists have to be sorted
        Collections.sort(sourceExpenses);
        Collections.sort(targetExpenses);

        while( true ) {
            if( sourceExpenses.size() <= i ) {
                // Done with all sourceExpenses
                return result;
            }
            Expense sourceExpense = sourceExpenses.get(i);
            if( targetExpenses.size() <= j ) {
                // Done with all targetExpenses
                result.add(sourceExpense);
            }
            Expense targetExpense = targetExpenses.get(j);
            if( sourceExpense.compareTo(targetExpense) == -1 ) {
                // This expense is smaller, add and skip
                result.add(sourceExpense);
                i++;
            } else if( sourceExpense.compareTo(targetExpense) == 1 ) {
                // This expense is larger, skip one target
                j++;
            } else {
                // Expenses are equal, skip both
                i++;
                j++;
            }
        }
    }

    public static Request withOptionalProxy( String proxy, Request request )
    {
        if (proxy != null && !proxy.isEmpty()) {
            request = request.viaProxy(proxy);
        }
        return request;
    }

    public static String getCsrfToken( Config config )
        throws URISyntaxException, IOException, RoleNotFoundException
    {
        if (csrfToken == null) {
            URIBuilder uriBuilder = new URIBuilder(config.getOutput() + "/rest/csrf");
            Response response = withOptionalProxy(config.getProxy(),
                Request.Get(uriBuilder.build())
                    .addHeader("Authorization", "Basic " + buildAuthString(config))
                    .addHeader("x-csrf-token", "fetch")).execute();
            Header responseCsrfHeader = response.returnResponse().getFirstHeader("x-csrf-token");
            if( responseCsrfHeader == null ) {
                throw new RoleNotFoundException("Provided username: \'" + config.getHcpUser()
                    + "\' is not authorized to perform http requests to HCP or wrong username/password provided.");
            }
            csrfToken = responseCsrfHeader.getValue();
            logger.info("Retrieved CSRF token " + csrfToken);
        }

        return csrfToken;
    }

    public static String buildAuthString( Config config )
    {
        return new String(Base64.encodeBase64((
            config.getHcpUser() + ":" + config.getHcpPass()
        ).getBytes()));
    }

    public static String getBodyFromResponse(HttpResponse r) throws IOException {
        InputStream in = r.getEntity().getContent();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (true) {
            int nread = in.read(buf, 0, buf.length);
            if (nread <= 0) {
                break;
            }
            baos.write(buf, 0, nread);
        }
        in.close();
        baos.close();
        byte[] bytes = baos.toByteArray();
        return new String(bytes);
    }
}
