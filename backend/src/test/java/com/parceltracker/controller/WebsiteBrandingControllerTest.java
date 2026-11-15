package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parceltracker.config.SecurityConfig;
import com.parceltracker.model.BrandingImage;
import com.parceltracker.model.WebsiteTheme;
import com.parceltracker.repository.BrandingImageRepository;
import com.parceltracker.repository.BrandingRepository;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.WebsiteThemeRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({WebsiteBrandingController.class, BrandingController.class})
@Import(SecurityConfig.class)
class WebsiteBrandingControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private BrandingRepository branding;
    @MockitoBean private WebsiteThemeRepository themes;
    @MockitoBean private BrandingImageRepository images;
    @MockitoBean private CourierAccountRepository accounts;

    @Test
    void servesDefaultAndSavedStylesheetsPublicly() throws Exception {
        when(themes.findById("default")).thenReturn(Optional.empty(),
                Optional.of(new WebsiteTheme("body { color: red; }", "Georgia")));
        mvc.perform(get("/api/branding/stylesheet"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("--canvas")));
        mvc.perform(get("/api/branding/stylesheet"))
                .andExpect(status().isOk())
                .andExpect(content().string("body { color: red; }"));
    }

    @Test
    void onlyAdminsCanChangeThemeAndInvalidFontIsRejected() throws Exception {
        String theme = "{\"css\":\"body { color: red; }\",\"fontFamily\":\"Georgia\"}";
        mvc.perform(put("/api/admin/website-theme").with(user("dispatch").roles("DISPATCHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(theme)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/website-theme").with(user("ops").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(theme)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/website-theme").with(user("ops").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(theme)).andExpect(status().isOk());
        verify(themes).save(any(WebsiteTheme.class));
        mvc.perform(put("/api/admin/website-theme").with(user("ops").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"css\":\"\",\"fontFamily\":\"not a font\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void imageUploadsAreValidatedAndImagesArePublic() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        String data = Base64.getEncoder().encodeToString(output.toByteArray());
        when(images.save(any(BrandingImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String upload = "{\"name\":\"Background\",\"contentType\":\"image/png\",\"data\":\"" + data + "\"}";
        mvc.perform(post("/api/admin/branding/images").with(user("ops").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(upload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").isNotEmpty());
        mvc.perform(post("/api/admin/branding/images").with(user("ops").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad\",\"contentType\":\"image/jpeg\",\"data\":\"" + data + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/branding/images").with(user("customer").roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(upload)).andExpect(status().isForbidden());
        when(images.findById("image-id")).thenReturn(Optional.of(
                new BrandingImage("image-id", "Background", "image/png", data)));
        mvc.perform(get("/api/branding/images/image-id")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(output.toByteArray()));
        mvc.perform(delete("/api/admin/branding/images/image-id").with(user("customer").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoMustBeAnActualImage() throws Exception {
        mvc.perform(put("/api/admin/branding").with(user("ops").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appName\":\"Example\",\"primaryColor\":\"#112233\",\"accentColor\":\"#445566\","
                        + "\"logoContentType\":\"image/png\",\"logoBase64\":\"AAAA\"}"))
                .andExpect(status().isBadRequest());
    }
}
