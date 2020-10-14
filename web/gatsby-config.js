module.exports = {
  siteMetadata: {
    title: `John Tipper's blog`,
    name: `John Tipper`,
    siteUrl: `https://johntipper.org`,
    description: `John Tipper - Cloud, software engineering and DevOps.`,
    hero: {
      heading: `Cloud, software engineering, DevOps.`,
      maxWidth: 960,
    },
    social: [
      {
        name: `twitter`,
        url: `https://twitter.com/john_tipper`,
      },
      {
        name: `github`,
        url: `https://github.com/john-tipper`,
      },
      {
        name: `linkedin`,
        url: `https://www.linkedin.com/in/john-tipper-5076395`,
      },
    ],
  },
  plugins: [
    `gatsby-plugin-sitemap`,
    {
      resolve: "@narative/gatsby-theme-novela",
      options: {
        contentPosts: "content/posts",
        contentAuthors: "content/authors",
        basePath: "/",
        authorsPage: true,
        articlePermalinkFormat: ":slug/",
        sources: {
          local: true,
          // contentful: true,
        },
      },
    },
    {
      resolve: `gatsby-plugin-manifest`,
      options: {
        name: `Novela by Narative`,
        short_name: `Novela`,
        start_url: `/`,
        background_color: `#fff`,
        theme_color: `#fff`,
        display: `standalone`,
        icon: `src/assets/favicon.png`,
      },
    },
    {
      resolve: `gatsby-plugin-netlify-cms`,
      options: {
      },
    },
  ],
};
