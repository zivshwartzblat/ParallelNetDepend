import java.util.regex.Pattern.Neg
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Future
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeoutException;
import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException
//import java.awt.List;
import java.util.concurrent.FutureTask;
import java.util.ArrayList
import java.util.HashMap;
import javax.net.ssl.SSLEngineResult.Status
import javax.swing.text.StyledEditorKit.BoldAction;
import Jenkins.*
import com.picscout.depend.dependency.main.Runner;
import com.picscout.depend.dependency.interfaces.ISolution;


class Globals {
	static HashMap<String, JenkinsJob> ReverseDependencyMap
	static def JobToRun
	static ArrayList<JenkinsJob> listOfJobsWithDepndentList = new ArrayList<JenkinsJob>()
}


map = [
	key_here: 'value_here.sln'
]


// Get the out variable
def config = new HashMap()
bindings = getBinding()
config.putAll(bindings.getVariables())

out = config['out']

out.println ' (*)Running script to find dependencies and run jobs(*)'
go()

out.println 'Finished script to find dependencies and run jobs'

def go() {
	def thr = Thread.currentThread()
	def build = thr?.executable
	def inputJobs = build.buildVariableResolver.resolve("JobsToRun")
	def envVarsMap = build.parent.builds[0].properties.get("envVars")
	def configPath = envVarsMap['config_file_path']
	def log4jPath = envVarsMap['log4j.configuration']

	println 'config path is: ' + configPath + ', log4Path is: ' +  log4jPath
	println 'going to calc depenencies , jobs to calc for are: ' + inputJobs

	if(inputJobs == null || inputJobs.isEmpty()) {
		println 'no jobs to run'
		return
	}

	ArrayList<String>  inputsolutionNames = new  ArrayList<String>()

	parseJobs(inputJobs, inputsolutionNames)

	def dependentsolutionNames

	dependentsolutionNames = getDependentSolutionNames(inputsolutionNames,configPath, log4jPath , true )

	getDependentsForEeachInputJobDependent(configPath, log4jPath)

	println " # List size = " + Globals.listOfJobsWithDepndentList.size()

	for(def j in Globals.listOfJobsWithDepndentList ){//debug
		println "job name:" + j.Name
		for(d in j.JobsDepndentList){
			println "	d: "+ d
		}
	}
}

def  getDependentsForEeachInputJobDependent(def configPath, def log4jPath){

	ArrayList<String>  depndentJobsNamesList = new ArrayList<String>()

	for(def jobIndex = 0 ; jobIndex < Globals.listOfJobsWithDepndentList.size() ; jobIndex++ ){
		if(Globals.listOfJobsWithDepndentList[jobIndex].JobsDepndentList != null){
			for(def depndentIndex = 0 ; depndentIndex < Globals.listOfJobsWithDepndentList[jobIndex].JobsDepndentList.size() ; depndentIndex++){

				String depndentJobName =  Globals.listOfJobsWithDepndentList[jobIndex].JobsDepndentList[depndentIndex]

				depndentJobsNamesList.clear()
				if(HasJobInJenkins(depndentJobName)){
					depndentJobsNamesList.add(depndentJobName)
				}

				if(!JobsWithDepndentListIsExsists(depndentJobName)){
					dependentsolutionNames = getDependentSolutionNames(depndentJobsNamesList,configPath, log4jPath , false )

					//need to add new job to the list
					if(HasJobInJenkins(depndentJobName)){
						AddInputSolutionToListOfJobsWithDepndentList(depndentJobName)
						for(depndentJob in dependentsolutionNames){
							Globals.listOfJobsWithDepndentList.getAt(GetInputSolutionIndex(depndentJobName)).JobsDepndentList.add(depndentJob)
						}
					}
				}
			}
		}
	}
}

def Boolean HasJobInJenkins(String depndentJobName){
	def depndentjob = depndentJobName.replace('.', '_')

	for(item in Hudson.instance.items){
		if(item.name.toLowerCase() == depndentjob.toLowerCase()){
			return true
		}
	}
	return false
}

def Boolean JobsWithDepndentListIsExsists(String jobName){

	for(def jobIndex = 0 ; jobIndex < Globals.listOfJobsWithDepndentList.size() ; jobIndex++ ){
		if(Globals.listOfJobsWithDepndentList[jobIndex].Name == jobName){
			return true
		}
	}
	return false
}

def getDependentSolutionNames(inputsolutionNames, configPath , log4jPath, AddJobsDepndentOption ) {

	ArrayList<String>  dependentsolutionNames = new  ArrayList<String>()
	Runner runner = new Runner(configPath, log4jPath);

	if(AddJobsDepndentOption){
		println ''
		println 'Calculating dependencies...'
		runner.calculateDependencies()
	}

	for(inputSolution in inputsolutionNames) {

		if(AddJobsDepndentOption){
			AddInputSolutionToListOfJobsWithDepndentList(inputSolution)
		}

		ArrayList<String>  tempNames = new  ArrayList<String>()
		tempNames.add(inputSolution)
		List<ISolution> result = runner.getSolutionsThatDependOnSolutionsByNames(tempNames);
		println 'For input solution: ' + inputSolution + ', got ' + result.size() + ' results, '
		for(ISolution sol : result) {
			solutionName = sol.getName().toLowerCase()
			if(solutionName.endsWith('.sln')) {
				solutionName = solutionName.substring(0, solutionName.length()-4)
			}
			if(!dependentsolutionNames.contains(solutionName)) {
				if(HasJobInJenkins(solutionName)){
					println 'Adding to dependent solution names :' + solutionName
					dependentsolutionNames.add(solutionName)
				}
				else{
					println "There isn't jenkins job for " + solutionName
				}

				if(AddJobsDepndentOption){
					if(HasJobInJenkins(solutionName)){
						jobName = solutionName.replace('_', '.')
						Globals.listOfJobsWithDepndentList.getAt(GetInputSolutionIndex(inputSolution)).JobsDepndentList.add(jobName)
					}
//					else{
//						//println "There isn't jenkins job for " + solutionName
//					}
				}
			}
			else {
				println 'Dependent solution name :' + jobName + ' already exists, will not add it'
			}
		}
	}
	return dependentsolutionNames
}

def int GetInputSolutionIndex(String inputSolution){
	for(int index = 0; index < Globals.listOfJobsWithDepndentList.size(); index++){
		if(Globals.listOfJobsWithDepndentList[index].Name == inputSolution){
			return index
		}
	}
}

def addInputJobs2JobsToRun(inputsolutionNames, dependentsolutionNames) {
	ArrayList<String>  solutions2Run = new ArrayList<String> (dependentsolutionNames)
	for(inputSolution in inputsolutionNames ) {
		if(!solutions2Run.contains(inputSolution)) {
			println 'Adding input solution:' + inputSolution + ' to the list of dependent solutions'
			solutions2Run.add(0, inputSolution)
		}
		else {
			println 'Input solution: ' + inputSolution + ' is already in dependent solutions list'
		}
	}
	return solutions2Run
}

def AddInputSolutionToListOfJobsWithDepndentList(String inputSolution){

	if(Globals.listOfJobsWithDepndentList.size() == 0){
		Globals.listOfJobsWithDepndentList.add(InitializeNewJob(inputSolution))
		return
	}

	if(Globals.listOfJobsWithDepndentList.size() > 0){
		if(CheckIfNeedToAddInputSolution(inputSolution)){
			Globals.listOfJobsWithDepndentList.add(InitializeNewJob(inputSolution))
		}
	}
}

def JenkinsJob InitializeNewJob(String inputSolution){
	def jobName = inputSolution.replace('_', '.')
	JenkinsJob job = new JenkinsJob()
	job.Name = jobName
	job.RunningStatus = "Wating"
	return job
}

Boolean CheckIfNeedToAddInputSolution(String inputSolution){
	def isContains = true
	if(Globals.listOfJobsWithDepndentList.size() > 0){
		for(job in Globals.listOfJobsWithDepndentList){
			if(job.Name == inputSolution){
				println "No need to add " + inputSolution+ " job"
				return false
			}
		}
	}
	println "Need to add " + inputSolution + " job"
	return true
}

def parseJobs(inputJobs, inputsolutionNames) {
	def inputJobsList = inputJobs.split(';')
	for(job in inputJobsList) {
		HandleJob(job, inputsolutionNames)
	}
	return  inputsolutionNames
}

def HandleJob(job, inputsolutionNames) {

	if(map[job] != null) {
		job = map[job]
	}
	else{
		job = job.replace('_', '.')
	}

	inputsolutionNames.add(job.toLowerCase())
	return inputsolutionNames
}

println ' '
println "Reverse Dependency start.."
Globals.ReverseDependencyMap = ReverseDependency(Globals.listOfJobsWithDepndentList)
println "Reverse Dependency finished."
println ' '

ArrayList<String> jobNames = new ArrayList<String>(Globals.ReverseDependencyMap.keySet());

println " "
println "# Jobs with depndent list:"
for (String jobName in jobNames){
	if(jobName!=null){
		println "job: "+ jobName
		JenkinsJob jobWithDepndentList = Globals.ReverseDependencyMap.get(jobName.replace('_', '.'));
		for(d in jobWithDepndentList.JobsDepndentList){
			if(d!=null){
				println "Depndent: " + d
			}
		}
	}
}

//if(!CircularDependencyChecking(jobNames)){
//	println "Circular dependency finished."
//	println "There is circular dependency"
//	exit 1 
//}
//println "Circular dependency finished."
println " "

try{
	def isRunning = true

	while(isRunning){
		isRunning = false;
		for (String jobName in jobNames){
			
			//println jobName
			String formattedJobName = jobName.replace('_', '.')
			JenkinsJob jobWithDepndentList = Globals.ReverseDependencyMap.get(formattedJobName);

			if(jobWithDepndentList.RunningStatus != "Done")
			{
				isRunning = true;
			}
			//println jobWithDepndentList.RunningStatus
			if(jobWithDepndentList.RunningStatus == "Waiting"){
				
				if(IsReady(jobWithDepndentList, Globals.ReverseDependencyMap)){
					jobWithDepndentList.RunningStatus = "Running"
					jobWithDepndentList.jobToRun = Hudson.instance.getJob(formattedJobName)
					println(jobWithDepndentList.Name + " submitted - " + jobWithDepndentList.RunningStatus)
					executeJob(jobName)
				}
			}
		}
		if (!isRunning)
		{
			break;
		}
		boolean keepWaiting = true;
		while(keepWaiting)
		{
			for (def jobWithDepndentList in Globals.ReverseDependencyMap)
			{
				if(jobWithDepndentList.value.RunningStatus != "Done"){

					if (jobWithDepndentList.value.future == null)
					{
						continue;
					}

					if (jobWithDepndentList.value.future.isDone())
					{

						def jobInMapFortmat = jobWithDepndentList.value.Name.replace('_', '.')
						Globals.ReverseDependencyMap.get(jobInMapFortmat).RunningStatus = "Done"
						println jobWithDepndentList.value.Name + " is Done"
						keepWaiting = false;

						def anotherBuild
						anotherBuild = jobWithDepndentList.value.future.get()
						println HyperlinkNote.encodeTo('/' + anotherBuild.url, anotherBuild.fullDisplayName) + " completed. Result was " + anotherBuild.result

						// Check that it succeeded
						build.result = anotherBuild.result
						//Globals.ReverseDependencyMap.get(jobInMapFortmat).future.result = anotherBuild.result
						if (anotherBuild.result != Result.SUCCESS && anotherBuild.result != Result.UNSTABLE) {
							// We abort this build right here and now.
							throw new AbortException("${anotherBuild.fullDisplayName} failed.")
						}
						break;
					}
				}
			}
			Thread.sleep(50);
		}
	}
	println foo(100)
}
catch (Exception e) {
	println "exception has occured! " + e.getMessage()
	println e.getStackTrace().toString()
	exit 1
}


def CircularDependencyChecking(def jobNames){
	println "Circular dependency checking..."
	for (String jobName in jobNames){
		if(jobName!=null){
			//println "job: "+ jobName
			JenkinsJob jobWithDepndentList = Globals.ReverseDependencyMap.get(jobName.replace('_', '.'));
			for(depndent in jobWithDepndentList.JobsDepndentList){
				if(depndent!=null){
					//println "here1: " + depndent
					JenkinsJob depndentList = Globals.ReverseDependencyMap.get(depndent.replace('_', '.'));
					for(d in depndentList.JobsDepndentList){
						if(d!=null){
							//println "here2: " + d
							if(jobName == d){
								println "(^) There is circular dependency:"
								println jobNmae + " depndent on" + d + " ,"
								println d + " depndent on" + jobName
								return false
							}
						}
					}
				}
			}
		}
	}
	return true
}


def executeJob(String jobName) throws Exception {

	jobName = GetJobNameInJenkins(jobName)

	//println "Job name in Jenkins: "+jobName

	JenkinsJob jobWithDepndentList =  Globals.ReverseDependencyMap.get(jobName)
	def thr = Thread.currentThread()
	def build = thr?.executable

	// Start another job
	def branch = build.buildVariableResolver.resolve("BranchToBuild")
	//Globals.JobToRun = Hudson.instance.getJob(jobName.replace('.', '_'))
	Globals.JobToRun = Hudson.instance.getJob(jobName)
	if( Globals.JobToRun == null) {
		println "could not find job: " + jobName
		return
	}

	try {
		def params = [new StringParameterValue('IsCalledFromAnotherJob', 'true'), new StringParameterValue('BranchToBuild', branch)]
		def future = Globals.JobToRun.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params))
		//jobWithDepndentList.future = future

		Globals.ReverseDependencyMap.get(jobName.toLowerCase().replace('_', '.')).future = future		//update the main list

		println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + Globals.JobToRun.url, Globals.JobToRun.fullDisplayName)

	} catch (CancellationException x) {
		println 'failed to run job: ' + jobName + ' exception is: ' x
		throw new AbortException("${jobToRun.fullDisplayName} aborted.")
	}
}

class JenkinsJob {
	public def Name
	public def JobsDepndentList = new  ArrayList<String>()
	public String RunningStatus = "Waiting"
	public String TestStatus = ""
	public def  jobToRun
	public Future<String> future
}

def  GetJobNameInJenkins(String jobName){
	def job = jobName.replace('.', '_')

	for(item in Hudson.instance.items){
		if(item.name.toLowerCase() == job.toLowerCase()){
			return item.name
		}
	}
}

def  boolean IsReady(def job, def reverseDependencyMap){

	for (String d in job.JobsDepndentList){
		def depJob = reverseDependencyMap[d.replace('_', '.')];
		
		if(depJob.RunningStatus != "Done"){
			//println "map " + depJob.RunningStatus
			return false
		}
	}
	return true
}

def HashMap<String, JenkinsJob> ReverseDependency(ArrayList<JenkinsJob> input){
	HashMap<String, JenkinsJob> jobMap = new HashMap<String, JenkinsJob>();
	InitializeHashMap(input, jobMap)

	for (JenkinsJob j in input) {
		for (String d in j.JobsDepndentList) {
			jobMap[d].JobsDepndentList.add(j.Name.replace('.', '_'));
		}
	}
	ReverseDependencyLog(jobMap, input)
	//println "Reverse Dependency Done"
	return jobMap;
}

def ReverseDependencyLog(HashMap jobMap, ArrayList input) {
	println "From map:"

	for (String job in jobMap.keySet()) {
		for (String d in jobMap[job].JobsDepndentList) {
			println job + " depends on " + d
		}
	}
}

def InitializeHashMap(ArrayList input, HashMap jobMap) {
	for (JenkinsJob j in input) {
		JenkinsJob jenkinsJob = new JenkinsJob()
		jenkinsJob.Name = j.Name.replace('.', '_')
		jobMap[j.Name] = jenkinsJob
	}
}

public static Object foo(int i) {
	if ( i < 100 ) {
		return -1
	}
	return 'Jenkins Prallel jobs runner finished.'
}