/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.fanarttv;

import static org.yamj.plugin.api.Constants.LANGUAGE_EN;

import com.omertron.fanarttvapi.model.FTArtwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.OnlineScanner;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import org.yamj.plugin.api.service.PluginMetadataService;
 
public abstract class AbstractFanartTvArtworkScanner implements OnlineScanner {

    private static final String LANGUAGE_NONE = "00";
    private static final String SCANNER_NAME = "fanarttv";

    protected PluginMetadataService metadataService;
    protected FanartTvApiWrapper fanartTvApiWrapper;
    protected Locale locale;

    @Override
    public final String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public void init(PluginConfigService configService, PluginMetadataService metadataService, PluginLocaleService localeService, CommonHttpClient httpClient) {
        this.metadataService = metadataService;
        this.fanartTvApiWrapper = FanartTvPlugin.getFanartTvApiWrapper();
        this.locale = localeService.getLocale();
    }

    protected static List<ArtworkDTO> getArtworkList(List<FTArtwork> ftArtwork, String language, int seasonNumber) {
        List<ArtworkDTO> artworkList = new ArrayList<>();
        final String season = Integer.toString(seasonNumber);
        
        // first try for default language
        for (FTArtwork artwork : ftArtwork) {
            if (!season.equals(artwork.getSeason())) {
                continue;
            }
            
            if (language.equalsIgnoreCase(artwork.getLanguage())) {
                ArtworkDTO aDto = new ArtworkDTO(SCANNER_NAME, artwork.getUrl());
                aDto.setLanguageCode(artwork.getLanguage());
                artworkList.add(aDto);
            } 
        }

        // try with English if nothing found with default language
        if (artworkList.isEmpty() && !LANGUAGE_EN.equalsIgnoreCase(language)) {
            for (FTArtwork artwork : ftArtwork) {
                if (!season.equals(artwork.getSeason())) {
                    continue;
                }

                if (LANGUAGE_EN.equalsIgnoreCase(artwork.getLanguage())) {
                    ArtworkDTO aDto = new ArtworkDTO(SCANNER_NAME, artwork.getUrl());
                    aDto.setLanguageCode(artwork.getLanguage());
                    artworkList.add(aDto);
                }
            }
        }

        // add artwork without language
        for (FTArtwork artwork : ftArtwork) {
            if (!season.equals(artwork.getSeason())) {
                continue;
            }

            if (LANGUAGE_NONE.equalsIgnoreCase(artwork.getLanguage())) {
                artworkList.add(new ArtworkDTO(SCANNER_NAME, artwork.getUrl()));
            }
        }

        return artworkList;
    }
}

