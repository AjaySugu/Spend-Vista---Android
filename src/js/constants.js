// Environment Configuration
const ENV_CONFIG = {
  stage: {
    name: 'stage',
    // apiUrl: 'https://api.stage.example.com',
    appUrl: 'https://stageapp.spendvista.com/'
  },
  preprod: {
    name: 'preprod',
    // apiUrl: 'https://api.preprod.example.com',
    appUrl: 'https://preprodapp.spendvista.com/'
  },
  prod: {
    name: 'prod',
    // apiUrl: 'https://api.example.com',
    appUrl: 'https://app.spendvista.com/'
  }
};

// Determine current environment (default to prod)
const getCurrentEnvironment = () => {
  const params = new URLSearchParams(window.location.search);
  const env = params.get('env');
  
  if (env && ENV_CONFIG[env]) {
    return env;
  }
  
  // Check localStorage for saved environment
  const savedEnv = localStorage.getItem('app_environment');
  if (savedEnv && ENV_CONFIG[savedEnv]) {
    return savedEnv;
  }
  
  return 'prod'; // Default environment
};

// Get current config
const getConfig = () => {
  const env = getCurrentEnvironment();
  return ENV_CONFIG[env];
};

// Set environment
const setEnvironment = (envName) => {
  if (ENV_CONFIG[envName]) {
    localStorage.setItem('app_environment', envName);
    return true;
  }
  return false;
};

// Export constants
const CONFIG = getConfig();

export { CONFIG, ENV_CONFIG, getCurrentEnvironment, getConfig, setEnvironment };
