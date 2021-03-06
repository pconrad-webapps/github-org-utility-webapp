package org.pconrad.webapps.sparkjava;

import java.util.HashMap;
import java.util.Map;
import static java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.ModelAndView;

import spark.Spark;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.before;

import spark.Request;
import spark.Response;

import org.pac4j.core.config.Config;
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.sparkjava.ApplicationLogoutRoute;

import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.sparkjava.SparkWebContext;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Contributor;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHOrganization;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;



import org.pac4j.oauth.profile.github.GitHubProfile;

import java.util.Collection;


import org.bson.Document;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;


import com.jayway.jsonpath.JsonPath;

/**
   Demo of Spark Pac4j with Github OAuth

   @author pconrad
 */
public class GithubOrgUtilityWebapp {

    private static final String DEFAULT_ORG_NAME = "UCSB-CS56-Projects";
    
    private static java.util.List<CommonProfile> getProfiles(final Request request,
						   final Response response) {
	final SparkWebContext context = new SparkWebContext(request, response);
	final ProfileManager manager = new ProfileManager(context);
	return manager.getAll(true);
    }    
    
    private final static MustacheTemplateEngine templateEngine = new MustacheTemplateEngine();

    /** 
	add github information to the session

    */
    private static Map addGithub(Map model, Request request, Response response) {
	GitHubProfile ghp = ((GitHubProfile)(model.get("ghp")));
	if (ghp == null) {
	    // System.out.println("No github profile");
	    return model;
	}
	try {
	    String accessToken = ghp.getAccessToken();
	    GitHub gh = null;
	    String org_name = model.get("org_name").toString();
	    gh =  GitHub.connect( model.get("userid").toString(), accessToken);
	    java.util.Map<java.lang.String,GHRepository> repos = null;
	    GHOrganization org = gh.getOrganization(org_name);
	    if (org != null) {
		repos = org.getRepositories();
	    }

	    java.util.HashMap<String, CS56ProjectRepo> cs56repos = 
	    	new java.util.HashMap<String, CS56ProjectRepo>();

	 	for (Map.Entry<String, GHRepository> entry : repos.entrySet()) {
		    String repoName = entry.getKey();
    		GHRepository repo = entry.getValue();

			java.util.List<GHIssue> issues = repo.getIssues(GHIssueState.OPEN);

    		// javadoc for GHRepository: http://github-api.kohsuke.org/apidocs/index.html

    		CS56ProjectRepo pr = new CS56ProjectRepo(
    				repoName,
    				repo.getUrl().toString(),
    				repo.getHtmlUrl().toString(),
    				repo.getDescription(),
    				issues.size()
    			);

    		cs56repos.put(repoName,pr);

		}		



	    if (org != null && cs56repos != null) {
		model.put("repos",cs56repos.entrySet());
	    } else {
		model.remove("repos");
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return model;
    }


    
    private static Map buildModel(Request request, Response response) {

	final Map model = new HashMap<String,Object>();
	
	Map<String, Object> map = new HashMap<String, Object>();
	for (String k: request.session().attributes()) {
	    Object v = request.session().attribute(k);
	    map.put(k,v);
	}
	
	model.put("session", map.entrySet());

	java.util.List<CommonProfile> userProfiles = getProfiles(request,response);

	map.put("profiles", userProfiles);

	try {
	    if (userProfiles.size()>0) {
		CommonProfile firstProfile = userProfiles.get(0);
		map.put("firstProfile", firstProfile);	
		
		GitHubProfile ghp = (GitHubProfile) firstProfile;
		model.put("ghp", ghp);
		model.put("userid",ghp.getUsername());
		model.put("name",ghp.getDisplayName());
		model.put("avatar_url",ghp.getPictureUrl());
		model.put("email",ghp.getEmail());
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}

	String new_org_name = DEFAULT_ORG_NAME;
	String requested_org_name = request.queryParams("org_name");
	String old_org_name = request.session().attribute("org_name");

	// Set the org name to the old one if it is non-null, non-blank
	// Unless the requested one form the form is non-null, non-blank.
	
	if (old_org_name != null && !old_org_name.trim().equals(""))
	    new_org_name = old_org_name.trim();
	if (requested_org_name !=null && !requested_org_name.trim().equals(""))
	    new_org_name = requested_org_name.trim();	
	request.session().attribute("org_name",new_org_name);
	model.put("org_name",new_org_name);
	return model;	
    }

    /**

       return a HashMap with values of all the environment variables
       listed; print error message for each missing one, and exit if any
       of them is not defined.
    */
    
    public static HashMap<String,String> getNeededEnvVars(String [] neededEnvVars) {
	HashMap<String,String> envVars = new HashMap<String,String>();
	
	
	for (String k:neededEnvVars) {
	    String v = System.getenv(k);
	    envVars.put(k,v);
	}
	
	boolean error=false;
	for (String k:neededEnvVars) {
	    if (envVars.get(k)==null) {
		error = true;
		System.err.println("Error: Must define env variable " + k);
	    }
	}
	if (error) { System.exit(1); }
	
	return envVars;
    }
    
    public static void main(String[] args) {

    Logger logger = LoggerFactory.getLogger(GithubOrgUtilityWebapp.class);
    logger.info("GithubOrgUtilityWebapp starting up");

	
	HashMap<String,String> envVars =
	    getNeededEnvVars(new String []{ "GITHUB_CLIENT_ID",
										"GITHUB_CLIENT_SECRET",
										"GITHUB_CALLBACK_URL",
										"APPLICATION_SALT",
										"MONGO_CLIENT_URI"});
	


	MongoClientURI mcuri = new MongoClientURI( envVars.get("MONGO_CLIENT_URI"));
	MongoClient mc = new MongoClient(mcuri);
	MongoDatabase database = mc.getDatabase(mcuri.getDatabase());			
	
	if (mc==null || database==null ) {
		logger.error("Mongo DB Authentication failed.  Check value of MONGO_CLIENT_URI env var");
		System.exit(3);
	}
	
	MongoCollection<Document> admin = database.getCollection("admin");
	if(admin==null){
		logger.error("Please add admin collection to mongo database");
		System.exit(3);
	}
	Document myDoc = admin.find().first();
	if(myDoc==null){
		logger.error("Please add at least one admin user");
		System.exit(3);
	}
	String json = myDoc.toJson();
	System.out.println("json="+json);
	String admin_github_id = JsonPath.parse(json).read("$.admin_github_id");
	System.out.println("admin_github_id="+admin_github_id);



	Spark.staticFileLocation("/static");
	
	try {
	    // needed for Heroku
	    Spark.port(Integer.valueOf(System.getenv("PORT"))); 
	} catch (Exception e) {
	    System.err.println("NOTICE: using default port." +
			       " Define PORT env variable to override");
	}

	Config config = new
	    GithubOAuthConfigFactory(envVars.get("GITHUB_CLIENT_ID"),
				     envVars.get("GITHUB_CLIENT_SECRET"),
				     envVars.get("GITHUB_CALLBACK_URL"),
				     envVars.get("APPLICATION_SALT"),
				     templateEngine,
				     "repo").build();

	final SecurityFilter
	    githubFilter = new SecurityFilter(config, "GithubClient", "", "");

	get("/",
	    (request, response) -> new ModelAndView(buildModel(request,response),"home.mustache"),
	    templateEngine);

	before("/login", githubFilter);

	get("/login",
	    (request, response) -> new ModelAndView(buildModel(request,response),"home.mustache"),
	    templateEngine);

	get("/logout", new ApplicationLogoutRoute(config, "/"));

	post("/setorg",
 	    (request, response) -> new ModelAndView(buildModel(request,response),"home.mustache"),
	    templateEngine);

	get("/setorg",
 	    (request, response) -> new ModelAndView(buildModel(request,response),"home.mustache"),
	    templateEngine);

	
	get("/session",
	    (request, response) -> new ModelAndView(buildModel(request,response),
						    "session.mustache"),
	    templateEngine);

	get("/github",
	    (request, response) ->
	    new ModelAndView(addGithub(buildModel(request,response),request,response),
			     "github.mustache"),
	    templateEngine);

	get("/repos-csv",
	    (request, response) ->
	    new ModelAndView(addGithub(buildModel(request,response),request,response),
			     "repos-csv.mustache"),
	    templateEngine);
	
	final org.pac4j.sparkjava.CallbackRoute callback =
	    new org.pac4j.sparkjava.CallbackRoute(config);

	get("/callback", callback);
	post("/callback", callback);

    }
}
