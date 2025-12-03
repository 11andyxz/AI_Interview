/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#8B5CF6', // Purple similar to screenshot
        secondary: '#F3F4F6',
      }
    },
  },
  plugins: [],
}

