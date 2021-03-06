package io.gr1d.portal.subscriptions.controller;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.loader.FixtureFactoryLoader;
import io.gr1d.core.model.CreatedResponse;
import io.gr1d.portal.subscriptions.SpringTestApplication;
import io.gr1d.portal.subscriptions.fixtures.ProviderFixtures.ProviderRule;
import io.gr1d.portal.subscriptions.request.ApiCreateRequest;
import io.gr1d.portal.subscriptions.request.ProviderRequest;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static io.gr1d.portal.subscriptions.TestUtils.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = SpringTestApplication.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, FlywayTestExecutionListener.class})
public class ProviderControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Flyway flyway;

    @Before
    public void init() {
        FixtureFactoryLoader.loadTemplates("io.gr1d.portal.subscriptions.fixtures");
    }

    @After
    public void clean() throws IllegalArgumentException {
        flyway.clean();
    }

    @Test
    @FlywayTest
    public void testProviderCreation() throws Exception {
        final ProviderRequest request = Fixture.from(ProviderRequest.class).gimme("valid");
        final String uri = "/provider";

        mockMvc.perform(get(uri)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(0)));

        final ResultActions actions = checkXss(mockMvc.perform(post("/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(request))).andDo(print()).andExpect(status().isCreated()));
        final CreatedResponse response = getResult(actions, CreatedResponse.class);
        final String uuid = response.getUuid();
        final String uriSingle = String.format("%s/%s", uri, uuid);

        mockMvc.perform(get(uri)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(1)))
                .andExpect(jsonPath("content[0].type").value(request.getType()))
                .andExpect(jsonPath("content[0].name").value(request.getName()))
                .andExpect(jsonPath("content[0].phone").value(request.getPhone()))
                .andExpect(jsonPath("content[0].email").value(request.getEmail()))
                .andExpect(jsonPath("content[0].uuid").value(uuid));

        mockMvc.perform(get(uriSingle).accept(MediaType.APPLICATION_JSON)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("name").value(request.getName()))
                .andExpect(jsonPath("phone").value(request.getPhone()))
                .andExpect(jsonPath("type").value(request.getType()))
                .andExpect(jsonPath("email").value(request.getEmail()))
                .andExpect(jsonPath("uuid").value(uuid));

        final ProviderRequest anotherRequest = Fixture.from(ProviderRequest.class).gimme("validPaid");

        checkXss(mockMvc.perform(put(uriSingle)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(anotherRequest))).andDo(print()).andExpect(status().isOk()));

        mockMvc.perform(get(uri)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(1)))
                .andExpect(jsonPath("content[0].name").value(anotherRequest.getName()))
                .andExpect(jsonPath("content[0].type").value(anotherRequest.getType()))
                .andExpect(jsonPath("content[0].wallet_id").value(anotherRequest.getWalletId()))
                .andExpect(jsonPath("content[0].phone").value(anotherRequest.getPhone()))
                .andExpect(jsonPath("content[0].email").value(anotherRequest.getEmail()))
                .andExpect(jsonPath("content[0].uuid").value(uuid));

        mockMvc.perform(get(uriSingle).accept(MediaType.APPLICATION_JSON)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("name").value(anotherRequest.getName()))
                .andExpect(jsonPath("type").value(anotherRequest.getType()))
                .andExpect(jsonPath("wallet_id").value(anotherRequest.getWalletId()))
                .andExpect(jsonPath("phone").value(anotherRequest.getPhone()))
                .andExpect(jsonPath("email").value(anotherRequest.getEmail()))
                .andExpect(jsonPath("uuid").value(uuid));
    }

    @Test
    @FlywayTest
    public void testProviderInactivation() throws Exception {
        final ProviderRequest request = Fixture.from(ProviderRequest.class).gimme("valid");
        final String uri = "/provider";
        final String uriApi = "/api";

        mockMvc.perform(get(uri)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(0)));

        final ResultActions actions = mockMvc.perform(post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(request))).andDo(print()).andExpect(status().isCreated());
        final CreatedResponse response = getResult(actions, CreatedResponse.class);
        final String uuid = response.getUuid();
        final String uriSingle = String.format("%s/%s", uri, uuid);
        final ApiCreateRequest apiRequest = Fixture.from(ApiCreateRequest.class).gimme("valid", new ProviderRule(uuid));

        mockMvc.perform(post(uriApi)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(apiRequest))).andDo(print()).andExpect(status().isCreated());

        mockMvc.perform(get(uri)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(1)))
                .andExpect(jsonPath("content[0].uuid").value(uuid));
        mockMvc.perform(get(uriSingle).accept(MediaType.APPLICATION_JSON)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("uuid").value(uuid));

        mockMvc.perform(get(uriApi)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(1)));

        mockMvc.perform(delete(uriSingle)).andDo(print()).andExpect(status().isOk());
        mockMvc.perform(get(uriSingle)).andDo(print()).andExpect(status().isOk())
                .andExpect(jsonPath("removed_at").isNotEmpty());
        mockMvc.perform(get(uri)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(0)));
        mockMvc.perform(delete(uriSingle)).andDo(print()).andExpect(status().isNotFound());

        // and then all APIs are removed
        mockMvc.perform(get(uriApi)).andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("content").isArray())
                .andExpect(jsonPath("content").value(hasSize(0)));
    }

    @Test
    @FlywayTest
    public void testNotFound() throws Exception {
        final String uri = "/provider/12y8e9129y8h";
        mockMvc.perform(get(uri)
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());

        final ProviderRequest request = Fixture.from(ProviderRequest.class).gimme("valid");

        checkXss(mockMvc.perform(put(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(request)))
                .andDo(print())
                .andExpect(status().isNotFound()));
    }

    @Test
    @FlywayTest
    public void testValidations() throws Exception {
        final ProviderRequest invalidName = Fixture.from(ProviderRequest.class).gimme("invalidName");
        final ProviderRequest invalidWalletId = Fixture.from(ProviderRequest.class).gimme("invalidWallet");
        final ProviderRequest invalidPhone = Fixture.from(ProviderRequest.class).gimme("invalidPhone");
        final ProviderRequest invalidEmail = Fixture.from(ProviderRequest.class).gimme("invalidEmail");

        checkXss(mockMvc.perform(post("/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(invalidName)))
                .andDo(print()).andExpect(status().isUnprocessableEntity()));

        checkXss(mockMvc.perform(post("/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(invalidWalletId)))
                .andDo(print()).andExpect(status().isUnprocessableEntity()));

        checkXss(mockMvc.perform(post("/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(invalidPhone)))
                .andDo(print()).andExpect(status().isUnprocessableEntity()));

        checkXss(mockMvc.perform(post("/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(invalidEmail)))
                .andDo(print()).andExpect(status().isUnprocessableEntity()));
    }

}
