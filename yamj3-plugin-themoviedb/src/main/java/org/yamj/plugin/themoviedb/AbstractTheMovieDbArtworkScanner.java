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
package org.yamj.plugin.themoviedb;

import static org.yamj.plugin.api.Constants.LANGUAGE_EN;

import com.omertron.themoviedbapi.MovieDbException;
import com.omertron.themoviedbapi.enumeration.ArtworkType;
import com.omertron.themoviedbapi.model.artwork.Artwork;
import com.omertron.themoviedbapi.results.ResultList;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.ArtworkTools;
 
public abstract class AbstractTheMovieDbArtworkScanner extends AbstractTheMovieDbScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractTheMovieDbArtworkScanner.class);
    protected static final String DEFAULT_SIZE = "original";

    /**
     * Get a list of the artwork matching type and size.
     * 
     * @param tmdbId
     * @param artworks
     * @param language
     * @param artworkType
     * @param artworkSize
     * @return
     */
    protected List<ArtworkDTO> filterArtwork(String tmdbId, ResultList<Artwork> resultList, String language, ArtworkType artworkType, String artworkSize) {
        List<ArtworkDTO> dtos = new ArrayList<>();

        if (resultList == null || resultList.isEmpty()) {
            LOG.debug("Got no {} artworks from TMDb for id {}", artworkType, tmdbId);
        } else {
            final List<Artwork> artworkList = resultList.getResults();
            LOG.debug("Got {} {} artworks from TMDb for id {}", artworkList.size(), artworkType, tmdbId);
            
            for (Artwork artwork : artworkList) {
                if (artwork.getArtworkType() == artworkType
                    && (StringUtils.isBlank(artwork.getLanguage()) // no language
                        || "xx".equalsIgnoreCase(artwork.getLanguage()) // another marker for no language
                        || artwork.getLanguage().equalsIgnoreCase(language))) // defined language
                {
                    this.addArtworkDTO(dtos, artwork, artworkType, artworkSize);
                }
            }
            
            if (dtos.isEmpty() && !LANGUAGE_EN.equalsIgnoreCase(language)) {
                // retrieve by English
                for (Artwork artwork : artworkList) {
                    if (artwork.getArtworkType() == artworkType && StringUtils.equalsIgnoreCase(artwork.getLanguage(), LANGUAGE_EN)) {
                        this.addArtworkDTO(dtos, artwork, artworkType, artworkSize);
                    }
                }
            }
            
            LOG.debug("Found {} {} artworks for TMDb id {} and language '{}'", dtos.size(), artworkType, tmdbId, language);
        }
        
        return dtos;
    }
    
    private void addArtworkDTO(List<ArtworkDTO> dtos, Artwork artwork, ArtworkType artworkType, String artworkSize) {
        try {
            URL artworkURL = theMovieDbApiWrapper.createImageURL(artwork, artworkSize);
            if (artworkURL == null || artworkURL.toString().endsWith("null")) {
                LOG.warn("{} URL is invalid and will not be used: {}", artworkType, artworkURL);
            } else {
                final String url = artworkURL.toString();
                dtos.add(new ArtworkDTO(getScannerName(), url, ArtworkTools.getPartialHashCode(url)));
            }
        } catch (MovieDbException ex) {
            LOG.warn("Failed to create artwork image URL: {}", ex.getMessage());
            LOG.trace("Image URL error", ex);
        }
    }
}

